package com.carebridge.webhooks;

import com.carebridge.cases.CaseService;
import com.carebridge.common.error.ApiException;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.identity.Tenant;
import com.carebridge.identity.TenantRepository;
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
  private final CaseService caseService;
  private final ObjectMapper objectMapper;
  private final OutboxService outboxService;

  public WebhookService(
      TenantRepository tenantRepository,
      WebhookSecretService webhookSecretService,
      WebhookEventRepository webhookEventRepository,
      CaseService caseService,
      ObjectMapper objectMapper,
      OutboxService outboxService) {
    this.tenantRepository = tenantRepository;
    this.webhookSecretService = webhookSecretService;
    this.webhookEventRepository = webhookEventRepository;
    this.caseService = caseService;
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

    caseService.openOrCommentOnLabFollowup(tenant.getId(), patientRef, testName, summary);
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
