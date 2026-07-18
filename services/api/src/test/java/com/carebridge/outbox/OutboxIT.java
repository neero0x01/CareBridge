package com.carebridge.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Seam: HTTP case transition + transactional outbox + worker (Testcontainers Postgres).
 *
 * <p>MUH-15: perform transition → outbox row appears and becomes processed via structured-log sink.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OutboxIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("carebridge")
          .withUsername("carebridge")
          .withPassword("carebridge");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "carebridge.jwt.secret",
        () -> "test-jwt-secret-must-be-at-least-32-bytes-long");
    registry.add("carebridge.auth.register-tenant-enabled", () -> "true");
    // Deterministic: tests drive the worker; avoid race with the scheduler.
    registry.add("carebridge.outbox.scheduling-enabled", () -> "false");
  }

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private OutboxMessageRepository outboxMessageRepository;
  @Autowired private OutboxWorker outboxWorker;

  @Test
  void transitionWritesOutboxRowAndWorkerMarksProcessed() {
    RegisteredAdmin admin = registerAdmin();
    String reviewerEmail = "reviewer@" + admin.slug() + ".example";
    inviteAndUnlock(admin, reviewerEmail, "REVIEWER");
    String reviewerToken = login(admin.slug(), reviewerEmail, "NewPass12!");

    ResponseEntity<String> created =
        createCase(admin.accessToken(), defaultCaseBody("Outbox transition"));
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String caseId = JsonPath.read(created.getBody(), "$.id");
    Number version = JsonPath.read(created.getBody(), "$.version");
    UUID caseUuid = UUID.fromString(caseId);

    List<OutboxMessageEntity> createdEvents =
        outboxMessageRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
            caseUuid, DomainEventTypes.CASE_CREATED);
    assertThat(createdEvents).hasSize(1);
    assertThat(createdEvents.getFirst().getProcessedAt()).isNull();
    assertThat(createdEvents.getFirst().getAttempts()).isEqualTo(0);
    assertThat(createdEvents.getFirst().getTenantId()).isNotNull();
    assertThat(createdEvents.getFirst().getPayloadJson()).contains("Outbox transition");

    ResponseEntity<String> claimed =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/claim",
            HttpMethod.POST,
            jsonEntity(reviewerToken, Map.of("version", version.longValue())),
            String.class);
    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    Number v1 = JsonPath.read(claimed.getBody(), "$.version");

    ResponseEntity<String> approved =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/transitions",
            HttpMethod.POST,
            jsonEntity(
                reviewerToken,
                Map.of(
                    "toStatus", "APPROVED",
                    "comment", "Looks good",
                    "version", v1.longValue())),
            String.class);
    assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<OutboxMessageEntity> transitionEvents =
        outboxMessageRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
            caseUuid, DomainEventTypes.CASE_TRANSITIONED);
    // claim (TO_DO→IN_REVIEW) + approve (IN_REVIEW→APPROVED)
    assertThat(transitionEvents).hasSize(2);
    assertThat(transitionEvents).allMatch(m -> m.getProcessedAt() == null);
    assertThat(transitionEvents.getLast().getPayloadJson()).contains("APPROVED");
    assertThat(transitionEvents.getLast().getPayloadJson()).contains("Looks good");

    List<OutboxMessageEntity> assignedEvents =
        outboxMessageRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
            caseUuid, DomainEventTypes.CASE_ASSIGNED);
    assertThat(assignedEvents).hasSize(1);
    assertThat(assignedEvents.getFirst().getProcessedAt()).isNull();

    int processed = outboxWorker.processPending();
    assertThat(processed).isGreaterThanOrEqualTo(1 + 2 + 1); // created + 2 transitions + assigned

    List<OutboxMessageEntity> after =
        outboxMessageRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
            caseUuid, DomainEventTypes.CASE_TRANSITIONED);
    assertThat(after).hasSize(2);
    assertThat(after).allMatch(m -> m.getProcessedAt() != null);
    assertThat(after).allMatch(m -> m.getAttempts() >= 1);

    OutboxMessageEntity createdAfter =
        outboxMessageRepository
            .findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                caseUuid, DomainEventTypes.CASE_CREATED)
            .getFirst();
    assertThat(createdAfter.getProcessedAt()).isNotNull();
    assertThat(createdAfter.getAttempts()).isEqualTo(1);
  }

  @Test
  void inviteWritesUserInvitedOutboxEvent() {
    RegisteredAdmin admin = registerAdmin();
    String inviteEmail = "invitee@" + admin.slug() + ".example";

    ResponseEntity<String> invite =
        restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "email", inviteEmail,
                    "fullName", "Invitee User",
                    "role", "CLINICIAN",
                    "temporaryPassword", "TempPass1!")),
            String.class);
    assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String userId = JsonPath.read(invite.getBody(), "$.id");

    List<OutboxMessageEntity> events =
        outboxMessageRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
            UUID.fromString(userId), DomainEventTypes.USER_INVITED);
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().getProcessedAt()).isNull();
    assertThat(events.getFirst().getPayloadJson()).contains(inviteEmail);
    assertThat(events.getFirst().getPayloadJson()).doesNotContain("TempPass1!");

    assertThat(outboxWorker.processPending()).isGreaterThanOrEqualTo(1);
    OutboxMessageEntity processed =
        outboxMessageRepository
            .findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                UUID.fromString(userId), DomainEventTypes.USER_INVITED)
            .getFirst();
    assertThat(processed.getProcessedAt()).isNotNull();
  }

  private Map<String, Object> defaultCaseBody(String title) {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    body.put("type", "REFERRAL");
    body.put("priority", "MEDIUM");
    body.put("patientDisplayName", "Pat Synthetic");
    body.put("patientRef", "PAT-OUTBOX");
    body.put("description", "notes");
    return body;
  }

  private ResponseEntity<String> createCase(String accessToken, Map<String, Object> body) {
    return restTemplate.exchange(
        "/api/v1/cases", HttpMethod.POST, jsonEntity(accessToken, body), String.class);
  }

  private RegisteredAdmin registerAdmin() {
    String slug = "clinic-" + UUID.randomUUID().toString().substring(0, 8);
    String email = "admin@" + slug + ".example";
    String password = "Str0ngPass!";
    ResponseEntity<String> register =
        restTemplate.postForEntity(
            "/api/v1/auth/register-tenant",
            Map.of(
                "tenantName", "Outbox Clinic",
                "slug", slug,
                "adminEmail", email,
                "adminPassword", password,
                "adminFullName", "Ada Admin"),
            String.class);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String accessToken = JsonPath.read(register.getBody(), "$.tokens.accessToken");
    return new RegisteredAdmin(slug, email, password, accessToken);
  }

  private void inviteAndUnlock(RegisteredAdmin admin, String email, String role) {
    ResponseEntity<String> invite =
        restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "email", email,
                    "fullName", "Invited " + role,
                    "role", role,
                    "temporaryPassword", "TempPass1!")),
            String.class);
    assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String token = login(admin.slug(), email, "TempPass1!");
    changePassword(token, "TempPass1!", "NewPass12!");
  }

  private String login(String tenantSlug, String email, String password) {
    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of("tenantSlug", tenantSlug, "email", email, "password", password),
            String.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    return JsonPath.read(login.getBody(), "$.accessToken");
  }

  private void changePassword(String accessToken, String currentPassword, String newPassword) {
    ResponseEntity<String> changed =
        restTemplate.exchange(
            "/api/v1/auth/change-password",
            HttpMethod.POST,
            jsonEntity(
                accessToken,
                Map.of("currentPassword", currentPassword, "newPassword", newPassword)),
            String.class);
    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private static HttpEntity<Map<String, Object>> jsonEntity(
      String accessToken, Map<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    return new HttpEntity<>(body, headers);
  }

  private record RegisteredAdmin(String slug, String email, String password, String accessToken) {}
}
