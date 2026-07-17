package com.carebridge.cases;

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
 * Seam: HTTP API {@code /api/v1/cases/{id}/comments} + terminal freeze (Testcontainers Postgres).
 *
 * <p>MUH-12: comment happy path and terminal Case immutability.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CaseCommentsIT {

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
  void commentHappyPathAuthorCanPostAndList() {
    RegisteredAdmin admin = registerAdmin();
    String clinicianEmail = "clin@" + admin.slug() + ".example";
    inviteAndUnlock(admin, clinicianEmail, "CLINICIAN");
    String clinicianToken = login(admin.slug(), clinicianEmail, "NewPass12!");
    String clinicianId = JsonPath.read(me(clinicianToken), "$.user.id");

    ResponseEntity<String> created =
        createCase(clinicianToken, defaultCaseBody("Comment case"));
    String caseId = JsonPath.read(created.getBody(), "$.id");

    ResponseEntity<String> posted =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/comments",
            HttpMethod.POST,
            jsonEntity(clinicianToken, Map.of("body", "Needs more patient history")),
            String.class);
    assertThat(posted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat((Object) JsonPath.read(posted.getBody(), "$.body"))
        .isEqualTo("Needs more patient history");
    assertThat((Object) JsonPath.read(posted.getBody(), "$.authorId")).isEqualTo(clinicianId);
    assertThat((Object) JsonPath.read(posted.getBody(), "$.caseId")).isEqualTo(caseId);

    ResponseEntity<String> listed =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/comments",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> bodies = JsonPath.read(listed.getBody(), "$[*].body");
    assertThat(bodies).containsExactly("Needs more patient history");
  }

  @Test
  void auditorCannotComment() {
    RegisteredAdmin admin = registerAdmin();
    String auditorEmail = "auditor@" + admin.slug() + ".example";
    inviteAndUnlock(admin, auditorEmail, "AUDITOR");
    String auditorToken = login(admin.slug(), auditorEmail, "NewPass12!");

    ResponseEntity<String> created =
        createCase(admin.accessToken(), defaultCaseBody("Auditor comment"));
    String caseId = JsonPath.read(created.getBody(), "$.id");

    ResponseEntity<String> denied =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/comments",
            HttpMethod.POST,
            jsonEntity(auditorToken, Map.of("body", "Should not work")),
            String.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat((Object) JsonPath.read(denied.getBody(), "$.code")).isEqualTo("FORBIDDEN");

    // Auditor may still read comments
    ResponseEntity<String> listed =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/comments",
            HttpMethod.GET,
            authEntity(auditorToken),
            String.class);
    assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void terminalCaseRejectsEditTransitionAndComment() {
    RegisteredAdmin admin = registerAdmin();
    String reviewerEmail = "rev@" + admin.slug() + ".example";
    inviteAndUnlock(admin, reviewerEmail, "REVIEWER");
    String reviewerToken = login(admin.slug(), reviewerEmail, "NewPass12!");

    ResponseEntity<String> created =
        createCase(admin.accessToken(), defaultCaseBody("Terminal freeze"));
    String caseId = JsonPath.read(created.getBody(), "$.id");
    Number version = JsonPath.read(created.getBody(), "$.version");

    // Reach APPROVED
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
                    "version", v1.longValue())),
            String.class);
    assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
    Number v2 = JsonPath.read(approved.getBody(), "$.version");

    Map<String, Object> patchBody = new HashMap<>();
    patchBody.put("title", "Mutate frozen");
    patchBody.put("version", v2.longValue());
    ResponseEntity<String> patchDenied =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId,
            HttpMethod.PATCH,
            jsonEntity(admin.accessToken(), patchBody),
            String.class);
    assertThat(patchDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat((Object) JsonPath.read(patchDenied.getBody(), "$.code")).isEqualTo("FORBIDDEN");
    assertThat((String) JsonPath.read(patchDenied.getBody(), "$.message"))
        .containsIgnoringCase("terminal");

    ResponseEntity<String> transitionDenied =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/transitions",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "toStatus", "IN_REVIEW",
                    "version", v2.longValue())),
            String.class);
    assertThat(transitionDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat((Object) JsonPath.read(transitionDenied.getBody(), "$.code")).isEqualTo("FORBIDDEN");
    assertThat((String) JsonPath.read(transitionDenied.getBody(), "$.message"))
        .containsIgnoringCase("terminal");

    ResponseEntity<String> commentDenied =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/comments",
            HttpMethod.POST,
            jsonEntity(admin.accessToken(), Map.of("body", "After approval")),
            String.class);
    assertThat(commentDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat((Object) JsonPath.read(commentDenied.getBody(), "$.code")).isEqualTo("FORBIDDEN");
    assertThat((String) JsonPath.read(commentDenied.getBody(), "$.message"))
        .containsIgnoringCase("terminal");
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
                "tenantName", "Comments Clinic",
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
