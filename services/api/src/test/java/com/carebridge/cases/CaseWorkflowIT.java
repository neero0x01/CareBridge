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
 * Seam: HTTP API {@code /api/v1/cases} claim/assign/transitions (Testcontainers Postgres).
 *
 * <p>MUH-11: workflow happy path, illegal transition, version conflict, assign rules.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CaseWorkflowIT {

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
  void claimThenApproveHappyPathRecordsTransitions() {
    RegisteredAdmin admin = registerAdmin();
    String reviewerEmail = "reviewer@" + admin.slug() + ".example";
    inviteAndUnlock(admin, reviewerEmail, "REVIEWER");
    String reviewerToken = login(admin.slug(), reviewerEmail, "NewPass12!");
    String reviewerId = JsonPath.read(me(reviewerToken), "$.user.id");

    ResponseEntity<String> created =
        createCase(admin.accessToken(), defaultCaseBody("Claim path"));
    String caseId = JsonPath.read(created.getBody(), "$.id");
    Number version = JsonPath.read(created.getBody(), "$.version");

    ResponseEntity<String> claimed =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/claim",
            HttpMethod.POST,
            jsonEntity(reviewerToken, Map.of("version", version.longValue())),
            String.class);
    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(claimed.getBody(), "$.status")).isEqualTo("IN_REVIEW");
    assertThat((Object) JsonPath.read(claimed.getBody(), "$.assigneeId")).isEqualTo(reviewerId);
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
    assertThat((Object) JsonPath.read(approved.getBody(), "$.status")).isEqualTo("APPROVED");
    assertThat((Object) JsonPath.read(approved.getBody(), "$.assigneeId")).isEqualTo(reviewerId);

    ResponseEntity<String> history =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/transitions",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> toStatuses = JsonPath.read(history.getBody(), "$[*].toStatus");
    assertThat(toStatuses).containsExactly("IN_REVIEW", "APPROVED");
    List<String> comments = JsonPath.read(history.getBody(), "$[*].comment");
    assertThat(comments).contains("Claimed", "Looks good");
  }

  @Test
  void illegalTransitionReturns409() {
    RegisteredAdmin admin = registerAdmin();
    ResponseEntity<String> created =
        createCase(admin.accessToken(), defaultCaseBody("Illegal"));
    String caseId = JsonPath.read(created.getBody(), "$.id");
    Number version = JsonPath.read(created.getBody(), "$.version");

    ResponseEntity<String> bad =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/transitions",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "toStatus", "APPROVED",
                    "version", version.longValue())),
            String.class);
    assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat((Object) JsonPath.read(bad.getBody(), "$.code")).isEqualTo("ILLEGAL_TRANSITION");
  }

  @Test
  void staleVersionOnClaimReturnsVersionConflict() {
    RegisteredAdmin admin = registerAdmin();
    String reviewerEmail = "rev@" + admin.slug() + ".example";
    inviteAndUnlock(admin, reviewerEmail, "REVIEWER");
    String reviewerToken = login(admin.slug(), reviewerEmail, "NewPass12!");

    ResponseEntity<String> created =
        createCase(admin.accessToken(), defaultCaseBody("Race"));
    String caseId = JsonPath.read(created.getBody(), "$.id");
    // Claim with wrong version
    ResponseEntity<String> stale =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/claim",
            HttpMethod.POST,
            jsonEntity(reviewerToken, Map.of("version", 99L)),
            String.class);
    assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat((Object) JsonPath.read(stale.getBody(), "$.code")).isEqualTo("VERSION_CONFLICT");
  }

  @Test
  void assignRequiresReviewerAssignee() {
    RegisteredAdmin admin = registerAdmin();
    String clinicianEmail = "clin@" + admin.slug() + ".example";
    inviteAndUnlock(admin, clinicianEmail, "CLINICIAN");
    String clinicianId = JsonPath.read(me(login(admin.slug(), clinicianEmail, "NewPass12!")), "$.user.id");

    String reviewerEmail = "rev@" + admin.slug() + ".example";
    inviteAndUnlock(admin, reviewerEmail, "REVIEWER");
    String reviewerId = JsonPath.read(me(login(admin.slug(), reviewerEmail, "NewPass12!")), "$.user.id");

    ResponseEntity<String> created =
        createCase(admin.accessToken(), defaultCaseBody("Assign me"));
    String caseId = JsonPath.read(created.getBody(), "$.id");
    Number version = JsonPath.read(created.getBody(), "$.version");

    ResponseEntity<String> badAssignee =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/assign",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "assigneeId", clinicianId,
                    "version", version.longValue())),
            String.class);
    assertThat(badAssignee.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat((Object) JsonPath.read(badAssignee.getBody(), "$.code")).isEqualTo("INVALID_ASSIGNEE");

    ResponseEntity<String> ok =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/assign",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "assigneeId", reviewerId,
                    "version", version.longValue())),
            String.class);
    assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(ok.getBody(), "$.status")).isEqualTo("IN_REVIEW");
    assertThat((Object) JsonPath.read(ok.getBody(), "$.assigneeId")).isEqualTo(reviewerId);
  }

  @Test
  void needsInfoKeepsAssigneeAndAllowsCreatorResubmit() {
    RegisteredAdmin admin = registerAdmin();
    String clinicianEmail = "clin@" + admin.slug() + ".example";
    inviteAndUnlock(admin, clinicianEmail, "CLINICIAN");
    String clinicianToken = login(admin.slug(), clinicianEmail, "NewPass12!");
    String reviewerEmail = "rev@" + admin.slug() + ".example";
    inviteAndUnlock(admin, reviewerEmail, "REVIEWER");
    String reviewerToken = login(admin.slug(), reviewerEmail, "NewPass12!");
    String reviewerId = JsonPath.read(me(reviewerToken), "$.user.id");

    ResponseEntity<String> created =
        createCase(clinicianToken, defaultCaseBody("Needs info path"));
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

    ResponseEntity<String> needsInfo =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/transitions",
            HttpMethod.POST,
            jsonEntity(
                reviewerToken,
                Map.of(
                    "toStatus", "NEEDS_INFO",
                    "comment", "More labs",
                    "version", v1.longValue())),
            String.class);
    assertThat(needsInfo.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(needsInfo.getBody(), "$.status")).isEqualTo("NEEDS_INFO");
    assertThat((Object) JsonPath.read(needsInfo.getBody(), "$.assigneeId")).isEqualTo(reviewerId);
    Number v2 = JsonPath.read(needsInfo.getBody(), "$.version");

    ResponseEntity<String> resubmit =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/transitions",
            HttpMethod.POST,
            jsonEntity(
                clinicianToken,
                Map.of(
                    "toStatus", "IN_REVIEW",
                    "comment", "Updated notes",
                    "version", v2.longValue())),
            String.class);
    assertThat(resubmit.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(resubmit.getBody(), "$.status")).isEqualTo("IN_REVIEW");
    assertThat((Object) JsonPath.read(resubmit.getBody(), "$.assigneeId")).isEqualTo(reviewerId);
  }

  @Test
  void clinicianCannotEditAfterClaim() {
    RegisteredAdmin admin = registerAdmin();
    String clinicianEmail = "clin@" + admin.slug() + ".example";
    inviteAndUnlock(admin, clinicianEmail, "CLINICIAN");
    String clinicianToken = login(admin.slug(), clinicianEmail, "NewPass12!");
    String reviewerEmail = "rev@" + admin.slug() + ".example";
    inviteAndUnlock(admin, reviewerEmail, "REVIEWER");
    String reviewerToken = login(admin.slug(), reviewerEmail, "NewPass12!");

    ResponseEntity<String> created =
        createCase(clinicianToken, defaultCaseBody("Edit gate"));
    String caseId = JsonPath.read(created.getBody(), "$.id");
    Number version = JsonPath.read(created.getBody(), "$.version");

    Map<String, Object> patchOk = new HashMap<>();
    patchOk.put("title", "Fixed title");
    patchOk.put("version", version.longValue());
    ResponseEntity<String> patched =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId,
            HttpMethod.PATCH,
            jsonEntity(clinicianToken, patchOk),
            String.class);
    assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
    Number v1 = JsonPath.read(patched.getBody(), "$.version");

    ResponseEntity<String> claimed =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId + "/claim",
            HttpMethod.POST,
            jsonEntity(reviewerToken, Map.of("version", v1.longValue())),
            String.class);
    assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
    Number v2 = JsonPath.read(claimed.getBody(), "$.version");

    Map<String, Object> patchBad = new HashMap<>();
    patchBad.put("title", "Too late");
    patchBad.put("version", v2.longValue());
    ResponseEntity<String> denied =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId,
            HttpMethod.PATCH,
            jsonEntity(clinicianToken, patchBad),
            String.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
                "tenantName", "Workflow Clinic",
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
