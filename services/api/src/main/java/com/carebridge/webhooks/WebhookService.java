package com.carebridge.webhooks;

import com.carebridge.cases.CaseCommentEntity;
import com.carebridge.cases.CaseCommentRepository;
import com.carebridge.cases.CaseEntity;
import com.carebridge.cases.CasePriority;
import com.carebridge.cases.CaseRepository;
import com.carebridge.cases.CaseStatus;
import com.carebridge.cases.CaseType;
import com.carebridge.cases.TenantCaseCounterRepository;
import com.carebridge.common.error.ApiException;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.identity.Role;
import com.carebridge.identity.Tenant;
import com.carebridge.identity.TenantRepository;
import com.carebridge.identity.User;
import com.carebridge.identity.UserRepository;
import com.carebridge.outbox.DomainEventTypes;
import com.carebridge.outbox.OutboxService;
import com.carebridge.webhooks.dto.InboundWebhookResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

  public static final String TYPE_LAB_RESULT_READY = "lab.result.ready";

  private final TenantRepository tenantRepository;
  private final WebhookSecretService webhookSecretService;
  private final WebhookEventRepository webhookEventRepository;
  private final CaseRepository caseRepository;
  private final CaseCommentRepository caseCommentRepository;
  private final TenantCaseCounterRepository counterRepository;
  private final UserRepository userRepository;
  private final ObjectMapper objectMapper;
  private final OutboxService outboxService;

  public WebhookService(
      TenantRepository tenantRepository,
      WebhookSecretService webhookSecretService,
      WebhookEventRepository webhookEventRepository,
      CaseRepository caseRepository,
      CaseCommentRepository caseCommentRepository,
      TenantCaseCounterRepository counterRepository,
      UserRepository userRepository,
      ObjectMapper objectMapper,
      OutboxService outboxService) {
    this.tenantRepository = tenantRepository;
    this.webhookSecretService = webhookSecretService;
    this.webhookEventRepository = webhookEventRepository;
    this.caseRepository = caseRepository;
    this.caseCommentRepository = caseCommentRepository;
    this.counterRepository = counterRepository;
    this.userRepository = userRepository;
    this.objectMapper = objectMapper;
    this.outboxService = outboxService;
  }

  /**
   * Ingest a signed inbound webhook. Returns {@code alreadyProcessed=true} for idempotent retries.
   *
   * @return outcome; caller maps new → 202, already processed → 200
   */
  @Transactional
  public InboundWebhookResponse processInbound(
      String tenantSlug, String eventIdHeader, String signatureHeader, byte[] rawBody) {
    if (tenantSlug == null || tenantSlug.isBlank()) {
      throw unauthorized("Unauthorized");
    }
    if (eventIdHeader == null || eventIdHeader.isBlank()) {
      throw unauthorized("Unauthorized");
    }
    if (rawBody == null) {
      throw unauthorized("Unauthorized");
    }

    String eventIdRaw = eventIdHeader.trim();
    UUID eventId;
    try {
      eventId = UUID.fromString(eventIdRaw);
    } catch (IllegalArgumentException ex) {
      throw unauthorized("Unauthorized");
    }
    // Sign over the wire event id string (header value), not a re-canonicalized UUID form.
    String eventIdForHmac = eventIdRaw;

    String slug = tenantSlug.trim().toLowerCase(Locale.ROOT);
    Tenant tenant =
        tenantRepository
            .findBySlug(slug)
            .orElseThrow(() -> unauthorized("Unauthorized"));

    String secret = webhookSecretService.decrypt(tenant.getWebhookSecretCiphertext());
    if (!WebhookHmac.verify(secret, eventIdForHmac, rawBody, signatureHeader)) {
      throw unauthorized("Unauthorized");
    }

    Optional<WebhookEventEntity> existing =
        webhookEventRepository.findByTenantIdAndId(tenant.getId(), eventId);
    if (existing.isPresent() && existing.get().getProcessedAt() != null) {
      return InboundWebhookResponse.ofAlreadyProcessed();
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(rawBody);
    } catch (Exception ex) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Invalid JSON body");
    }

    String type = textOrNull(root, "type");
    if (type == null || type.isBlank()) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "type is required");
    }
    if (!TYPE_LAB_RESULT_READY.equals(type)) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Unsupported webhook type: " + type);
    }

    JsonNode payload = root.get("payload");
    if (payload == null || !payload.isObject()) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "payload is required");
    }
    String patientRef = textOrNull(payload, "patientRef");
    if (patientRef == null || patientRef.isBlank()) {
      throw new ApiException(
          ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "payload.patientRef is required");
    }
    patientRef = patientRef.trim();
    String testName = optionalText(payload, "testName", "lab result");
    String summary = optionalText(payload, "summary", "");

    Instant now = Instant.now();
    String payloadJson = new String(rawBody, StandardCharsets.UTF_8);

    if (existing.isEmpty()) {
      WebhookEventEntity event =
          new WebhookEventEntity(
              eventId, tenant.getId(), type, payloadJson, true, null, now);
      try {
        webhookEventRepository.saveAndFlush(event);
      } catch (DataIntegrityViolationException ex) {
        // Concurrent insert of same event_id — re-read winner.
        existing = webhookEventRepository.findByTenantIdAndId(tenant.getId(), eventId);
        if (existing.isPresent() && existing.get().getProcessedAt() != null) {
          return InboundWebhookResponse.ofAlreadyProcessed();
        }
        // Winner still processing or crashed mid-way: do not double-apply.
        return InboundWebhookResponse.ofAlreadyProcessed();
      }
      existing = webhookEventRepository.findByTenantIdAndId(tenant.getId(), eventId);
    }

    WebhookEventEntity event =
        existing.orElseThrow(() -> unauthorized("Unauthorized"));
    if (event.getProcessedAt() != null) {
      return InboundWebhookResponse.ofAlreadyProcessed();
    }

    applyLabResultReady(tenant, patientRef, testName, summary, now);
    event.setProcessedAt(now);
    Map<String, Object> outboxPayload = new LinkedHashMap<>();
    outboxPayload.put("eventId", event.getId().toString());
    outboxPayload.put("type", event.getType());
    outboxPayload.put("tenantId", tenant.getId().toString());
    outboxPayload.put("patientRef", patientRef);
    outboxService.enqueue(
        tenant.getId(),
        DomainEventTypes.AGGREGATE_WEBHOOK_EVENT,
        event.getId(),
        DomainEventTypes.WEBHOOK_PROCESSED,
        outboxPayload);
    return InboundWebhookResponse.ofNew();
  }

  private void applyLabResultReady(
      Tenant tenant, String patientRef, String testName, String summary, Instant now) {
    Optional<CaseEntity> open =
        caseRepository
            .findFirstByTenantIdAndPatientRefAndTypeAndStatusInOrderByCreatedAtAsc(
                tenant.getId(),
                patientRef,
                CaseType.LAB_FOLLOWUP,
                OpenLabCaseMatcher.openStatuses());

    UUID systemActorId = requireSystemActorId(tenant.getId());

    if (open.isPresent()) {
      String body = formatLabComment(testName, summary);
      caseCommentRepository.save(
          new CaseCommentEntity(
              UUID.randomUUID(),
              open.get().getId(),
              tenant.getId(),
              systemActorId,
              body,
              now));
      return;
    }

    long seq = counterRepository.allocateNext(tenant.getId());
    String caseNumber = "CB-" + seq;
    String title = "Lab follow-up: " + testName;
    String description = summary.isBlank() ? "Inbound lab.result.ready" : summary.trim();
    CaseEntity entity =
        new CaseEntity(
            UUID.randomUUID(),
            tenant.getId(),
            caseNumber,
            title,
            CaseType.LAB_FOLLOWUP,
            CasePriority.MEDIUM,
            CaseStatus.TO_DO,
            patientRef,
            patientRef,
            description,
            systemActorId,
            null,
            now,
            now);
    caseRepository.save(entity);
    Map<String, Object> casePayload = new LinkedHashMap<>();
    casePayload.put("id", entity.getId().toString());
    casePayload.put("caseNumber", entity.getCaseNumber());
    casePayload.put("title", entity.getTitle());
    casePayload.put("type", entity.getType().name());
    casePayload.put("priority", entity.getPriority().name());
    casePayload.put("status", entity.getStatus().name());
    casePayload.put("patientRef", entity.getPatientRef());
    casePayload.put("createdBy", entity.getCreatedBy().toString());
    casePayload.put("source", "webhook");
    outboxService.enqueue(
        tenant.getId(),
        DomainEventTypes.AGGREGATE_CASE,
        entity.getId(),
        DomainEventTypes.CASE_CREATED,
        casePayload);
  }

  private UUID requireSystemActorId(UUID tenantId) {
    return userRepository.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
        .filter(u -> u.getRole() == Role.ORG_ADMIN && u.isActive())
        .map(User::getId)
        .findFirst()
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No active ORG_ADMIN for system actor"));
  }

  private static String formatLabComment(String testName, String summary) {
    if (summary == null || summary.isBlank()) {
      return "Lab result ready: " + testName;
    }
    return "Lab result ready: " + testName + " — " + summary.trim();
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode v = node.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    return v.asText();
  }

  private static String optionalText(JsonNode node, String field, String defaultValue) {
    String v = textOrNull(node, field);
    if (v == null || v.isBlank()) {
      return defaultValue;
    }
    return v.trim();
  }

  private static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, message);
  }
}
