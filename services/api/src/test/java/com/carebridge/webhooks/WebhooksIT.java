package com.carebridge.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
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
 * Seam: HTTP API {@code /api/v1/webhooks} + register-tenant webhook secret (Testcontainers Postgres).
 *
 * <p>MUH-13: secret on register/rotate, HMAC verify, idempotency, create vs comment paths.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WebhooksIT {

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
    // Base64 of 32 ASCII bytes (AES-256)
    registry.add(
        "carebridge.webhooks.encryption-key-base64",
        () -> "dGVzdC13ZWJob29rLWVuY3J5cHRpb24ta2V5ISEhISE=");
  }

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void registerReturnsWebhookSecretOnceAndSecretNotOnMe() {
    RegisteredTenant tenant = registerTenant();

    assertThat(tenant.webhookSecret()).startsWith("whsec_");
    assertThat(tenant.webhookSecret()).isNotBlank();

    ResponseEntity<String> me =
        restTemplate.exchange(
            "/api/v1/me", HttpMethod.GET, authEntity(tenant.accessToken()), String.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody()).doesNotContain(tenant.webhookSecret());
    assertThat(me.getBody()).doesNotContain("webhookSecret");
  }

  @Test
  void orgAdminCanRotateSecretAndOldSignatureFails() {
    RegisteredTenant tenant = registerTenant();
    String oldSecret = tenant.webhookSecret();
    String body =
        """
        {"type":"lab.result.ready","payload":{"patientRef":"PAT-ROT","testName":"CBC","summary":"ok"}}
        """;
    UUID eventId = UUID.randomUUID();

    ResponseEntity<String> rotate =
        restTemplate.exchange(
            "/api/v1/webhooks/secret/rotate",
            HttpMethod.POST,
            authEntity(tenant.accessToken()),
            String.class);
    assertThat(rotate.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(rotate.getBody()).isNotNull();
    String newSecret = JsonPath.read(rotate.getBody(), "$.webhookSecret");
    assertThat(newSecret).startsWith("whsec_");
    assertThat(newSecret).isNotEqualTo(oldSecret);

    ResponseEntity<String> withOld =
        postInbound(tenant.slug(), eventId, oldSecret, body);
    assertThat(withOld.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    ResponseEntity<String> withNew =
        postInbound(tenant.slug(), eventId, newSecret, body);
    assertThat(withNew.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat((Object) JsonPath.read(withNew.getBody(), "$.accepted")).isEqualTo(true);
    assertThat((Object) JsonPath.read(withNew.getBody(), "$.alreadyProcessed")).isEqualTo(false);
  }

  @Test
  void badSignatureIsUnauthorized() {
    RegisteredTenant tenant = registerTenant();
    String body =
        """
        {"type":"lab.result.ready","payload":{"patientRef":"PAT-BAD","testName":"X","summary":"y"}}
        """;
    UUID eventId = UUID.randomUUID();

    ResponseEntity<String> response =
        postInbound(tenant.slug(), eventId, "whsec_wrong_secret_value___________", body);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void labResultReadyCreatesLabFollowupWhenNoOpenCase() {
    RegisteredTenant tenant = registerTenant();
    String body =
        """
        {"type":"lab.result.ready","payload":{"patientRef":"PAT-NEW","testName":"HbA1c","summary":"Synthetic result for demo"}}
        """;
    UUID eventId = UUID.randomUUID();

    ResponseEntity<String> inbound =
        postInbound(tenant.slug(), eventId, tenant.webhookSecret(), body);
    assertThat(inbound.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat((Object) JsonPath.read(inbound.getBody(), "$.accepted")).isEqualTo(true);
    assertThat((Object) JsonPath.read(inbound.getBody(), "$.alreadyProcessed")).isEqualTo(false);

    ResponseEntity<String> list =
        restTemplate.exchange(
            "/api/v1/cases?q=PAT-NEW",
            HttpMethod.GET,
            authEntity(tenant.accessToken()),
            String.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(list.getBody(), "$.totalElements")).isEqualTo(1);
    assertThat((Object) JsonPath.read(list.getBody(), "$.content[0].type")).isEqualTo("LAB_FOLLOWUP");
    assertThat((Object) JsonPath.read(list.getBody(), "$.content[0].status")).isEqualTo("TO_DO");
    assertThat((Object) JsonPath.read(list.getBody(), "$.content[0].patientRef")).isEqualTo("PAT-NEW");
    assertThat((Object) JsonPath.read(list.getBody(), "$.content[0].title"))
        .isEqualTo("Lab follow-up: HbA1c");
    assertThat((Object) JsonPath.read(list.getBody(), "$.content[0].description"))
        .isEqualTo("Synthetic result for demo");
    String caseId = JsonPath.read(list.getBody(), "$.content[0].id");
    // Full parity: Case create via lab path writes audit
    ResponseEntity<String> audit =
        restTemplate.exchange(
            "/api/v1/audit?entityType=Case&entityId=" + caseId,
            HttpMethod.GET,
            authEntity(tenant.accessToken()),
            String.class);
    assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> actions = JsonPath.read(audit.getBody(), "$.content[*].action");
    assertThat(actions).contains("CASE_CREATED");
  }

  @Test
  void listUsersHidesSystemActorAndLoginAsSystemFails() {
    RegisteredTenant tenant = registerTenant();

    ResponseEntity<String> users =
        restTemplate.exchange(
            "/api/v1/users", HttpMethod.GET, authEntity(tenant.accessToken()), String.class);
    assertThat(users.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> emails = JsonPath.read(users.getBody(), "$[*].email");
    assertThat(emails).doesNotContain("system@carebridge.internal");
    assertThat(emails).contains(tenant.email());

    ResponseEntity<String> loginAsSystem =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug",
                tenant.slug(),
                "email",
                "system@carebridge.internal",
                "password",
                "password"),
            String.class);
    assertThat(loginAsSystem.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void labResultReadyCommentsOnOpenLabFollowupInsteadOfCreating() {
    RegisteredTenant tenant = registerTenant();

    // Seed an open LAB_FOLLOWUP via cases API
    ResponseEntity<String> created =
        restTemplate.exchange(
            "/api/v1/cases",
            HttpMethod.POST,
            authEntity(
                tenant.accessToken(),
                Map.of(
                    "title", "Existing lab",
                    "type", "LAB_FOLLOWUP",
                    "priority", "MEDIUM",
                    "patientDisplayName", "Pat Open",
                    "patientRef", "PAT-OPEN",
                    "description", "Already open")),
            String.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String caseId = JsonPath.read(created.getBody(), "$.id");

    String body =
        """
        {"type":"lab.result.ready","payload":{"patientRef":"PAT-OPEN","testName":"Lipid panel","summary":"All synthetic"}}
        """;
    UUID eventId = UUID.randomUUID();

    ResponseEntity<String> inbound =
        postInbound(tenant.slug(), eventId, tenant.webhookSecret(), body);
    assertThat(inbound.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    ResponseEntity<String> list =
        restTemplate.exchange(
            "/api/v1/cases?q=PAT-OPEN",
            HttpMethod.GET,
            authEntity(tenant.accessToken()),
            String.class);
    assertThat((Object) JsonPath.read(list.getBody(), "$.totalElements")).isEqualTo(1);

    ResponseEntity<String> comments =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/comments",
            HttpMethod.GET,
            authEntity(tenant.accessToken()),
            String.class);
    assertThat(comments.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> bodies = JsonPath.read(comments.getBody(), "$[*].body");
    assertThat(bodies).hasSize(1);
    assertThat(bodies.get(0)).isEqualTo("Lab result ready: Lipid panel — All synthetic");

    ResponseEntity<String> audit =
        restTemplate.exchange(
            "/api/v1/audit?entityType=Case&entityId=" + caseId,
            HttpMethod.GET,
            authEntity(tenant.accessToken()),
            String.class);
    assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> actions = JsonPath.read(audit.getBody(), "$.content[*].action");
    assertThat(actions).contains("CASE_COMMENT_ADDED");
  }

  @Test
  void sameEventIdIsIdempotentNoDoubleCreate() {
    RegisteredTenant tenant = registerTenant();
    String body =
        """
        {"type":"lab.result.ready","payload":{"patientRef":"PAT-IDEM","testName":"TSH","summary":"once"}}
        """;
    UUID eventId = UUID.randomUUID();

    ResponseEntity<String> first =
        postInbound(tenant.slug(), eventId, tenant.webhookSecret(), body);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat((Object) JsonPath.read(first.getBody(), "$.alreadyProcessed")).isEqualTo(false);

    ResponseEntity<String> second =
        postInbound(tenant.slug(), eventId, tenant.webhookSecret(), body);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(second.getBody(), "$.accepted")).isEqualTo(true);
    assertThat((Object) JsonPath.read(second.getBody(), "$.alreadyProcessed")).isEqualTo(true);

    ResponseEntity<String> list =
        restTemplate.exchange(
            "/api/v1/cases?q=PAT-IDEM",
            HttpMethod.GET,
            authEntity(tenant.accessToken()),
            String.class);
    assertThat((Object) JsonPath.read(list.getBody(), "$.totalElements")).isEqualTo(1);
  }

  @Test
  void terminalLabFollowupDoesNotCountAsOpenCreatesNewCase() {
    RegisteredTenant tenant = registerTenant();

    // Create LAB_FOLLOWUP, claim as reviewer path is heavy — use assign after invite reviewer then
    // transition. Simpler: create case then force terminal via reviewer workflow.
    String reviewerEmail = "reviewer@" + tenant.slug() + ".example";
    ResponseEntity<String> invite =
        restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            authEntity(
                tenant.accessToken(),
                Map.of(
                    "email", reviewerEmail,
                    "fullName", "Rev Viewer",
                    "role", "REVIEWER",
                    "temporaryPassword", "TmpPass1!")),
            String.class);
    assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", tenant.slug(),
                "email", reviewerEmail,
                "password", "TmpPass1!"),
            String.class);
    String reviewerToken = JsonPath.read(login.getBody(), "$.accessToken");

    restTemplate.exchange(
        "/api/v1/auth/change-password",
        HttpMethod.POST,
        authEntity(
            reviewerToken,
            Map.of("currentPassword", "TmpPass1!", "newPassword", "RevPass1!")),
        String.class);
    login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", tenant.slug(),
                "email", reviewerEmail,
                "password", "RevPass1!"),
            String.class);
    reviewerToken = JsonPath.read(login.getBody(), "$.accessToken");

    ResponseEntity<String> created =
        restTemplate.exchange(
            "/api/v1/cases",
            HttpMethod.POST,
            authEntity(
                tenant.accessToken(),
                Map.of(
                    "title", "Terminal lab",
                    "type", "LAB_FOLLOWUP",
                    "priority", "LOW",
                    "patientDisplayName", "Pat Done",
                    "patientRef", "PAT-TERM",
                    "description", "will approve")),
            String.class);
    String caseId = JsonPath.read(created.getBody(), "$.id");
    int version = JsonPath.read(created.getBody(), "$.version");

    ResponseEntity<String> claimed =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/claim",
            HttpMethod.POST,
            authEntity(reviewerToken, Map.of("version", version)),
            String.class);
    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    version = JsonPath.read(claimed.getBody(), "$.version");

    ResponseEntity<String> approved =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/transitions",
            HttpMethod.POST,
            authEntity(
                reviewerToken,
                Map.of("toStatus", "APPROVED", "version", version, "comment", "done")),
            String.class);
    assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);

    String body =
        """
        {"type":"lab.result.ready","payload":{"patientRef":"PAT-TERM","testName":"Repeat","summary":"new work"}}
        """;
    ResponseEntity<String> inbound =
        postInbound(tenant.slug(), UUID.randomUUID(), tenant.webhookSecret(), body);
    assertThat(inbound.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    ResponseEntity<String> list =
        restTemplate.exchange(
            "/api/v1/cases?q=PAT-TERM",
            HttpMethod.GET,
            authEntity(tenant.accessToken()),
            String.class);
    assertThat((Object) JsonPath.read(list.getBody(), "$.totalElements")).isEqualTo(2);
  }

  @Test
  void nonAdminCannotRotateSecret() {
    RegisteredTenant tenant = registerTenant();
    String clinicianEmail = "clin@" + tenant.slug() + ".example";
    restTemplate.exchange(
        "/api/v1/users",
        HttpMethod.POST,
        authEntity(
            tenant.accessToken(),
            Map.of(
                "email", clinicianEmail,
                "fullName", "Clin Ician",
                "role", "CLINICIAN",
                "temporaryPassword", "TmpPass1!")),
        String.class);

    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", tenant.slug(),
                "email", clinicianEmail,
                "password", "TmpPass1!"),
            String.class);
    String token = JsonPath.read(login.getBody(), "$.accessToken");
    restTemplate.exchange(
        "/api/v1/auth/change-password",
        HttpMethod.POST,
        authEntity(token, Map.of("currentPassword", "TmpPass1!", "newPassword", "ClinPass1!")),
        String.class);
    login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", tenant.slug(),
                "email", clinicianEmail,
                "password", "ClinPass1!"),
            String.class);
    token = JsonPath.read(login.getBody(), "$.accessToken");

    ResponseEntity<String> rotate =
        restTemplate.exchange(
            "/api/v1/webhooks/secret/rotate",
            HttpMethod.POST,
            authEntity(token),
            String.class);
    assertThat(rotate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private ResponseEntity<String> postInbound(
      String slug, UUID eventId, String secret, String rawBody) {
    byte[] bodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
    String signature =
        "sha256=" + WebhookHmac.signHex(secret, eventId.toString(), bodyBytes);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-CareBridge-Tenant", slug);
    headers.set("X-CareBridge-Event-Id", eventId.toString());
    headers.set("X-CareBridge-Signature", signature);

    return restTemplate.exchange(
        "/api/v1/webhooks/inbound",
        HttpMethod.POST,
        new HttpEntity<>(bodyBytes, headers),
        String.class);
  }

  private RegisteredTenant registerTenant() {
    String slug = "wh-" + UUID.randomUUID().toString().substring(0, 8);
    String email = "admin@" + slug + ".example";
    ResponseEntity<String> register =
        restTemplate.postForEntity(
            "/api/v1/auth/register-tenant",
            Map.of(
                "tenantName", "Webhook Clinic",
                "slug", slug,
                "adminEmail", email,
                "adminPassword", "Str0ngPass!",
                "adminFullName", "Ada Admin"),
            String.class);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(register.getBody()).isNotNull();
    String access = JsonPath.read(register.getBody(), "$.tokens.accessToken");
    String webhookSecret = JsonPath.read(register.getBody(), "$.webhookSecret");
    return new RegisteredTenant(slug, email, access, webhookSecret);
  }

  private HttpEntity<Void> authEntity(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    return new HttpEntity<>(headers);
  }

  private HttpEntity<?> authEntity(String accessToken, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    return new HttpEntity<>(body, headers);
  }

  private record RegisteredTenant(
      String slug, String email, String accessToken, String webhookSecret) {}
}
