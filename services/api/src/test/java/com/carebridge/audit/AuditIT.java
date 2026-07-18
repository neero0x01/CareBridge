package com.carebridge.audit;

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
 * Seam: HTTP API {@code /api/v1/audit} (Testcontainers Postgres).
 *
 * <p>MUH-14: transition writes audit row visible via API; ORG_ADMIN/AUDITOR only; filters.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuditIT {

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
  }

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void transitionWritesAuditRowVisibleViaApi() {
    RegisteredAdmin admin = registerAdmin();
    String reviewerEmail = "reviewer@" + admin.slug() + ".example";
    inviteAndUnlock(admin, reviewerEmail, "REVIEWER");
    String reviewerToken = login(admin.slug(), reviewerEmail, "NewPass12!");
    String reviewerId = JsonPath.read(me(reviewerToken), "$.user.id");

    ResponseEntity<String> created =
        createCase(admin.accessToken(), defaultCaseBody("Audit transition"));
    String caseId = JsonPath.read(created.getBody(), "$.id");
    Number version = JsonPath.read(created.getBody(), "$.version");

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

    ResponseEntity<String> audit =
        restTemplate.exchange(
            "/api/v1/audit?entityType=Case&entityId=" + caseId + "&size=50",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<String> actions = JsonPath.read(audit.getBody(), "$.content[*].action");
    assertThat(actions).contains("CASE_CREATED", "CASE_TRANSITIONED");

    List<String> entityTypes = JsonPath.read(audit.getBody(), "$.content[*].entityType");
    assertThat(entityTypes).allMatch("Case"::equals);

    List<String> entityIds = JsonPath.read(audit.getBody(), "$.content[*].entityId");
    assertThat(entityIds).allMatch(caseId::equals);

    // Transition row includes before/after status
    List<Map<String, Object>> content = JsonPath.read(audit.getBody(), "$.content");
    Map<String, Object> transitionRow =
        content.stream()
            .filter(row -> "CASE_TRANSITIONED".equals(row.get("action")))
            .filter(
                row -> {
                  Object after = row.get("after");
                  return after instanceof Map
                      && "APPROVED".equals(((Map<?, ?>) after).get("status"));
                })
            .findFirst()
            .orElseThrow();
    assertThat(transitionRow.get("actorId")).isEqualTo(reviewerId);
    assertThat(transitionRow.get("before")).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) transitionRow.get("before")).get("status")).isEqualTo("IN_REVIEW");
    assertThat(((Map<?, ?>) transitionRow.get("after")).get("status")).isEqualTo("APPROVED");
  }

  @Test
  void clinicianAndReviewerCannotAccessAuditApi() {
    RegisteredAdmin admin = registerAdmin();
    inviteAndUnlock(admin, "clin@" + admin.slug() + ".example", "CLINICIAN");
    inviteAndUnlock(admin, "rev@" + admin.slug() + ".example", "REVIEWER");
    String clinToken = login(admin.slug(), "clin@" + admin.slug() + ".example", "NewPass12!");
    String revToken = login(admin.slug(), "rev@" + admin.slug() + ".example", "NewPass12!");

    for (String token : List.of(clinToken, revToken)) {
      ResponseEntity<String> res =
          restTemplate.exchange(
              "/api/v1/audit", HttpMethod.GET, authEntity(token), String.class);
      assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Test
  void auditorCanListAuditAndFiltersByEntityType() {
    RegisteredAdmin admin = registerAdmin();
    inviteAndUnlock(admin, "auditor@" + admin.slug() + ".example", "AUDITOR");
    String auditorToken =
        login(admin.slug(), "auditor@" + admin.slug() + ".example", "NewPass12!");

    createCase(admin.accessToken(), defaultCaseBody("For audit list"));

    ResponseEntity<String> all =
        restTemplate.exchange(
            "/api/v1/audit?size=50", HttpMethod.GET, authEntity(auditorToken), String.class);
    assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
    Number total = JsonPath.read(all.getBody(), "$.totalElements");
    assertThat(total.longValue()).isGreaterThanOrEqualTo(1L);

    ResponseEntity<String> usersOnly =
        restTemplate.exchange(
            "/api/v1/audit?entityType=User&size=50",
            HttpMethod.GET,
            authEntity(auditorToken),
            String.class);
    assertThat(usersOnly.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> userEntityTypes = JsonPath.read(usersOnly.getBody(), "$.content[*].entityType");
    assertThat(userEntityTypes).isNotEmpty().allMatch("User"::equals);

    ResponseEntity<String> casesOnly =
        restTemplate.exchange(
            "/api/v1/audit?entityType=Case&size=50",
            HttpMethod.GET,
            authEntity(auditorToken),
            String.class);
    assertThat(casesOnly.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> caseEntityTypes = JsonPath.read(casesOnly.getBody(), "$.content[*].entityType");
    assertThat(caseEntityTypes).isNotEmpty().allMatch("Case"::equals);
  }

  @Test
  void inviteAndUserPatchWriteAuditEntries() {
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

    ResponseEntity<String> patched =
        restTemplate.exchange(
            "/api/v1/users/" + userId,
            HttpMethod.PATCH,
            jsonEntity(admin.accessToken(), Map.of("role", "REVIEWER", "active", false)),
            String.class);
    assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> audit =
        restTemplate.exchange(
            "/api/v1/audit?entityType=User&entityId=" + userId + "&size=50",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> actions = JsonPath.read(audit.getBody(), "$.content[*].action");
    assertThat(actions).contains("USER_INVITED", "USER_UPDATED");
  }

  private Map<String, Object> defaultCaseBody(String title) {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    body.put("type", "REFERRAL");
    body.put("priority", "MEDIUM");
    body.put("patientDisplayName", "Pat Synthetic");
    body.put("patientRef", "PAT-001");
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
                "tenantName", "Audit Clinic",
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

  private String me(String accessToken) {
    ResponseEntity<String> me =
        restTemplate.exchange("/api/v1/me", HttpMethod.GET, authEntity(accessToken), String.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    return me.getBody();
  }

  private static HttpEntity<Void> authEntity(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    return new HttpEntity<>(headers);
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
