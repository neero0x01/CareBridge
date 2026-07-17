package com.carebridge.cases;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * Seam: HTTP API {@code /api/v1/cases} (Testcontainers Postgres).
 *
 * <p>MUH-10: create, list/filters/pagination, get, role gates, cross-tenant 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CasesIT {

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
  void orgAdminCanCreateCaseInToDoWithCaseNumberAndCreator() {
    RegisteredAdmin admin = registerAdmin();
    String userId = JsonPath.read(me(admin.accessToken()), "$.user.id");

    ResponseEntity<String> created = createCase(admin.accessToken(), defaultCaseBody("Referral review", "HIGH"));

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(created.getBody(), "$.title")).isEqualTo("Referral review");
    assertThat((Object) JsonPath.read(created.getBody(), "$.type")).isEqualTo("REFERRAL");
    assertThat((Object) JsonPath.read(created.getBody(), "$.priority")).isEqualTo("HIGH");
    assertThat((Object) JsonPath.read(created.getBody(), "$.status")).isEqualTo("TO_DO");
    assertThat((Object) JsonPath.read(created.getBody(), "$.patientDisplayName"))
        .isEqualTo("Pat Synthetic");
    assertThat((Object) JsonPath.read(created.getBody(), "$.patientRef")).isEqualTo("PAT-001");
    assertThat((Object) JsonPath.read(created.getBody(), "$.description"))
        .isEqualTo("Synthetic referral notes");
    assertThat((Object) JsonPath.read(created.getBody(), "$.createdBy")).isEqualTo(userId);
    assertThat((Object) JsonPath.read(created.getBody(), "$.assigneeId")).isNull();
    assertThat((Object) JsonPath.read(created.getBody(), "$.version")).isEqualTo(0);
    String caseNumber = JsonPath.read(created.getBody(), "$.caseNumber");
    assertThat(caseNumber).matches("CB-\\d+");
    assertThat((Object) JsonPath.read(created.getBody(), "$.id")).isNotNull();
  }

  @Test
  void getCaseByIdReturnsSameTenantCase() {
    RegisteredAdmin admin = registerAdmin();
    ResponseEntity<String> created =
        createCase(admin.accessToken(), defaultCaseBody("Get me", "MEDIUM"));
    String caseId = JsonPath.read(created.getBody(), "$.id");
    String caseNumber = JsonPath.read(created.getBody(), "$.caseNumber");

    ResponseEntity<String> got =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId,
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);

    assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(got.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(got.getBody(), "$.id")).isEqualTo(caseId);
    assertThat((Object) JsonPath.read(got.getBody(), "$.caseNumber")).isEqualTo(caseNumber);
    assertThat((Object) JsonPath.read(got.getBody(), "$.title")).isEqualTo("Get me");
  }

  @Test
  void listSupportsFiltersAndPagination() {
    RegisteredAdmin admin = registerAdmin();
    createCase(admin.accessToken(), caseBody("Alpha referral", "REFERRAL", "HIGH", "PAT-A", "note A"));
    createCase(admin.accessToken(), caseBody("Beta discharge", "DISCHARGE", "LOW", "PAT-B", "note B"));
    createCase(admin.accessToken(), caseBody("Gamma lab", "LAB_FOLLOWUP", "URGENT", "PAT-C", "note C"));

    ResponseEntity<String> byStatus =
        restTemplate.exchange(
            "/api/v1/cases?status=TO_DO&page=0&size=10",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(byStatus.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(byStatus.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(byStatus.getBody(), "$.totalElements")).isEqualTo(3);
    List<String> titles = JsonPath.read(byStatus.getBody(), "$.content[*].title");
    assertThat(titles).contains("Alpha referral", "Beta discharge", "Gamma lab");

    ResponseEntity<String> byPriority =
        restTemplate.exchange(
            "/api/v1/cases?priority=URGENT",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(byPriority.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> urgentTitles = JsonPath.read(byPriority.getBody(), "$.content[*].title");
    assertThat(urgentTitles).containsExactly("Gamma lab");

    ResponseEntity<String> byQ =
        restTemplate.exchange(
            "/api/v1/cases?q=PAT-B",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(byQ.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> qTitles = JsonPath.read(byQ.getBody(), "$.content[*].title");
    assertThat(qTitles).containsExactly("Beta discharge");

    ResponseEntity<String> page0 =
        restTemplate.exchange(
            "/api/v1/cases?page=0&size=2",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(page0.getBody(), "$.totalElements")).isEqualTo(3);
    assertThat((Object) JsonPath.read(page0.getBody(), "$.size")).isEqualTo(2);
    assertThat((Object) JsonPath.read(page0.getBody(), "$.number")).isEqualTo(0);
    List<?> page0Content = JsonPath.read(page0.getBody(), "$.content");
    assertThat(page0Content).hasSize(2);

    ResponseEntity<String> unassigned =
        restTemplate.exchange(
            "/api/v1/cases?assignee=unassigned",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(unassigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(unassigned.getBody(), "$.totalElements")).isEqualTo(3);
  }

  @Test
  void crossTenantGetAndPatchReturn404() {
    RegisteredAdmin tenantA = registerAdmin();
    RegisteredAdmin tenantB = registerAdmin();
    inviteAndUnlock(tenantB, "auditor@" + tenantB.slug() + ".example", "AUDITOR");
    String auditorB =
        login(tenantB.slug(), "auditor@" + tenantB.slug() + ".example", "NewPass12!");

    ResponseEntity<String> created =
        createCase(tenantA.accessToken(), defaultCaseBody("Secret case", "HIGH"));
    String caseId = JsonPath.read(created.getBody(), "$.id");

    ResponseEntity<String> getCross =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId,
            HttpMethod.GET,
            authEntity(tenantB.accessToken()),
            String.class);
    assertThat(getCross.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(getCross.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(getCross.getBody(), "$.code")).isEqualTo("NOT_FOUND");

    Map<String, Object> patchBody = new HashMap<>();
    patchBody.put("title", "Hijacked");
    patchBody.put("version", 0);
    ResponseEntity<String> patchCross =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId,
            HttpMethod.PATCH,
            jsonEntity(tenantB.accessToken(), patchBody),
            String.class);
    assertThat(patchCross.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(patchCross.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(patchCross.getBody(), "$.code")).isEqualTo("NOT_FOUND");

    // AUDITOR of other tenant must also see 404, not role 403 (no ID leakage)
    ResponseEntity<String> patchAuditor =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId,
            HttpMethod.PATCH,
            jsonEntity(auditorB, patchBody),
            String.class);
    assertThat(patchAuditor.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat((Object) JsonPath.read(patchAuditor.getBody(), "$.code")).isEqualTo("NOT_FOUND");

    // Still intact for owner
    ResponseEntity<String> getOwner =
        restTemplate.exchange(
            "/api/v1/cases/" + caseId,
            HttpMethod.GET,
            authEntity(tenantA.accessToken()),
            String.class);
    assertThat(getOwner.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(getOwner.getBody(), "$.title")).isEqualTo("Secret case");
  }

  @Test
  void clinicianCanCreateAuditorCannotCreateAllRolesCanRead() {
    RegisteredAdmin admin = registerAdmin();
    inviteAndUnlock(admin, "clinician@" + admin.slug() + ".example", "CLINICIAN");
    inviteAndUnlock(admin, "reviewer@" + admin.slug() + ".example", "REVIEWER");
    inviteAndUnlock(admin, "auditor@" + admin.slug() + ".example", "AUDITOR");

    String clinicianToken =
        login(admin.slug(), "clinician@" + admin.slug() + ".example", "NewPass12!");
    String reviewerToken =
        login(admin.slug(), "reviewer@" + admin.slug() + ".example", "NewPass12!");
    String auditorToken =
        login(admin.slug(), "auditor@" + admin.slug() + ".example", "NewPass12!");

    ResponseEntity<String> clinicianCreate =
        createCase(clinicianToken, defaultCaseBody("Clinician case", "MEDIUM"));
    assertThat(clinicianCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String caseId = JsonPath.read(clinicianCreate.getBody(), "$.id");

    ResponseEntity<String> auditorCreate =
        createCase(auditorToken, defaultCaseBody("Auditor case", "LOW"));
    assertThat(auditorCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> reviewerCreate =
        createCase(reviewerToken, defaultCaseBody("Reviewer case", "LOW"));
    assertThat(reviewerCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    for (String token : List.of(admin.accessToken(), clinicianToken, reviewerToken, auditorToken)) {
      ResponseEntity<String> list =
          restTemplate.exchange(
              "/api/v1/cases", HttpMethod.GET, authEntity(token), String.class);
      assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
      List<String> ids = JsonPath.read(list.getBody(), "$.content[*].id");
      assertThat(ids).contains(caseId);

      ResponseEntity<String> got =
          restTemplate.exchange(
              "/api/v1/cases/" + caseId, HttpMethod.GET, authEntity(token), String.class);
      assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  @Test
  void concurrentCreatesProduceUniqueCaseNumbers() throws Exception {
    RegisteredAdmin admin = registerAdmin();
    int n = 20;
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<ResponseEntity<String>>> futures =
          IntStream.range(0, n)
              .mapToObj(
                  i ->
                      pool.submit(
                          () -> {
                            start.await();
                            return createCase(
                                admin.accessToken(),
                                defaultCaseBody("Concurrent " + i, "MEDIUM"));
                          }))
              .collect(Collectors.toList());
      start.countDown();

      Set<String> numbers = new HashSet<>();
      for (Future<ResponseEntity<String>> future : futures) {
        ResponseEntity<String> response = future.get(30, TimeUnit.SECONDS);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String caseNumber = JsonPath.read(response.getBody(), "$.caseNumber");
        assertThat(caseNumber).matches("CB-\\d+");
        assertThat(numbers.add(caseNumber))
            .as("duplicate case_number under concurrency: %s", caseNumber)
            .isTrue();
      }
      assertThat(numbers).hasSize(n);
    } finally {
      pool.shutdownNow();
    }
  }

  private Map<String, Object> defaultCaseBody(String title, String priority) {
    return caseBody(title, "REFERRAL", priority, "PAT-001", "Synthetic referral notes");
  }

  private Map<String, Object> caseBody(
      String title, String type, String priority, String patientRef, String description) {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    body.put("type", type);
    body.put("priority", priority);
    body.put("patientDisplayName", "Pat Synthetic");
    body.put("patientRef", patientRef);
    body.put("description", description);
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
                "tenantName", "Cases Clinic",
                "slug", slug,
                "adminEmail", email,
                "adminPassword", password,
                "adminFullName", "Ada Admin"),
            String.class);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(register.getBody()).isNotNull();
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
            Map.of(
                "tenantSlug", tenantSlug,
                "email", email,
                "password", password),
            String.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login.getBody()).isNotNull();
    return JsonPath.read(login.getBody(), "$.accessToken");
  }

  private void changePassword(String accessToken, String currentPassword, String newPassword) {
    ResponseEntity<String> changed =
        restTemplate.exchange(
            "/api/v1/auth/change-password",
            HttpMethod.POST,
            jsonEntity(
                accessToken,
                Map.of(
                    "currentPassword", currentPassword,
                    "newPassword", newPassword)),
            String.class);
    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  private String me(String accessToken) {
    ResponseEntity<String> me =
        restTemplate.exchange(
            "/api/v1/me", HttpMethod.GET, authEntity(accessToken), String.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody()).isNotNull();
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
