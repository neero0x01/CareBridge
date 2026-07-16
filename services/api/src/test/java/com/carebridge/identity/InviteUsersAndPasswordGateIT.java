package com.carebridge.identity;

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
 * Seam: HTTP API {@code /api/v1} (Testcontainers Postgres).
 *
 * <p>MUH-8: invite users, list/patch (ORG_ADMIN), mustChangePassword gate, change-password unlock,
 * inactive login rejection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InviteUsersAndPasswordGateIT {

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
  void orgAdminInviteCreatesUserWithMustChangePassword() {
    RegisteredAdmin admin = registerAdmin();
    String inviteEmail = "clinician@" + admin.slug() + ".example";

    ResponseEntity<String> invite =
        restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "email", inviteEmail,
                    "fullName", "Casey Clinician",
                    "role", "CLINICIAN",
                    "temporaryPassword", "TempPass1!")),
            String.class);

    assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(invite.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(invite.getBody(), "$.email")).isEqualTo(inviteEmail);
    assertThat((Object) JsonPath.read(invite.getBody(), "$.fullName")).isEqualTo("Casey Clinician");
    assertThat((Object) JsonPath.read(invite.getBody(), "$.role")).isEqualTo("CLINICIAN");
    assertThat((Object) JsonPath.read(invite.getBody(), "$.active")).isEqualTo(true);
    assertThat((Object) JsonPath.read(invite.getBody(), "$.mustChangePassword")).isEqualTo(true);
    assertThat(invite.getBody()).doesNotContain("TempPass1!");
    assertThat(invite.getBody()).doesNotContain("passwordHash");

    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", admin.slug(),
                "email", inviteEmail,
                "password", "TempPass1!"),
            String.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void orgAdminCanListAndPatchUsers() {
    RegisteredAdmin admin = registerAdmin();
    String inviteEmail = "reviewer@" + admin.slug() + ".example";

    ResponseEntity<String> invite =
        restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "email", inviteEmail,
                    "fullName", "Riley Reviewer",
                    "role", "REVIEWER",
                    "temporaryPassword", "TempPass1!")),
            String.class);
    assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String invitedId = JsonPath.read(invite.getBody(), "$.id");

    ResponseEntity<String> list =
        restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.GET,
            authEntity(admin.accessToken()),
            String.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(list.getBody()).isNotNull();
    List<String> emails = JsonPath.read(list.getBody(), "$[*].email");
    assertThat(emails).contains(admin.email(), inviteEmail);

    Map<String, Object> patchBody = new HashMap<>();
    patchBody.put("role", "AUDITOR");
    patchBody.put("active", false);
    ResponseEntity<String> patched =
        restTemplate.exchange(
            "/api/v1/users/" + invitedId,
            HttpMethod.PATCH,
            jsonEntity(admin.accessToken(), patchBody),
            String.class);
    assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(patched.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(patched.getBody(), "$.role")).isEqualTo("AUDITOR");
    assertThat((Object) JsonPath.read(patched.getBody(), "$.active")).isEqualTo(false);
  }

  @Test
  void nonOrgAdminCannotListUsers() {
    RegisteredAdmin admin = registerAdmin();
    String inviteEmail = "clinician@" + admin.slug() + ".example";
    inviteUser(admin, inviteEmail, "CLINICIAN", "TempPass1!");

    String clinicianToken = login(admin.slug(), inviteEmail, "TempPass1!");
    // Still mustChangePassword — change password first so gate does not mask role check
    changePassword(clinicianToken, "TempPass1!", "NewPass12!");
    String unlocked = login(admin.slug(), inviteEmail, "NewPass12!");

    ResponseEntity<String> list =
        restTemplate.exchange(
            "/api/v1/users", HttpMethod.GET, authEntity(unlocked), String.class);
    assertThat(list.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(list.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(list.getBody(), "$.code")).isEqualTo("FORBIDDEN");
  }

  @Test
  void mustChangePasswordBlocksNonAuthApisUntilPasswordChanged() {
    RegisteredAdmin admin = registerAdmin();
    String inviteEmail = "coadmin@" + admin.slug() + ".example";
    inviteUser(admin, inviteEmail, "ORG_ADMIN", "TempPass1!");

    String invitedToken = login(admin.slug(), inviteEmail, "TempPass1!");

    ResponseEntity<String> meWhileGated =
        restTemplate.exchange(
            "/api/v1/me", HttpMethod.GET, authEntity(invitedToken), String.class);
    assertThat(meWhileGated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Object) JsonPath.read(meWhileGated.getBody(), "$.user.mustChangePassword"))
        .isEqualTo(true);

    ResponseEntity<String> usersWhileGated =
        restTemplate.exchange(
            "/api/v1/users", HttpMethod.GET, authEntity(invitedToken), String.class);
    assertThat(usersWhileGated.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(usersWhileGated.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(usersWhileGated.getBody(), "$.code"))
        .isEqualTo("MUST_CHANGE_PASSWORD");

    ResponseEntity<String> changed =
        restTemplate.exchange(
            "/api/v1/auth/change-password",
            HttpMethod.POST,
            jsonEntity(
                invitedToken,
                Map.of(
                    "currentPassword", "TempPass1!",
                    "newPassword", "NewPass12!")),
            String.class);
    assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(changed.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(changed.getBody(), "$.mustChangePassword")).isEqualTo(false);
    assertThat(changed.getBody()).doesNotContain("NewPass12!");

    // Token remains valid; flag is loaded from DB so unlock applies without re-login
    ResponseEntity<String> usersAfter =
        restTemplate.exchange(
            "/api/v1/users", HttpMethod.GET, authEntity(invitedToken), String.class);
    assertThat(usersAfter.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(usersAfter.getBody()).isNotNull();
    List<String> emails = JsonPath.read(usersAfter.getBody(), "$[*].email");
    assertThat(emails).contains(admin.email(), inviteEmail);

    // New password works; old temp password does not
    assertThat(login(admin.slug(), inviteEmail, "NewPass12!")).isNotBlank();
    ResponseEntity<String> oldLogin =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", admin.slug(),
                "email", inviteEmail,
                "password", "TempPass1!"),
            String.class);
    assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void inactiveUserCannotLogin() {
    RegisteredAdmin admin = registerAdmin();
    String inviteEmail = "inactive@" + admin.slug() + ".example";
    ResponseEntity<String> invite =
        restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "email", inviteEmail,
                    "fullName", "Inactive User",
                    "role", "CLINICIAN",
                    "temporaryPassword", "TempPass1!")),
            String.class);
    assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String userId = JsonPath.read(invite.getBody(), "$.id");

    Map<String, Object> patchBody = new HashMap<>();
    patchBody.put("active", false);
    ResponseEntity<String> patched =
        restTemplate.exchange(
            "/api/v1/users/" + userId,
            HttpMethod.PATCH,
            jsonEntity(admin.accessToken(), patchBody),
            String.class);
    assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", admin.slug(),
                "email", inviteEmail,
                "password", "TempPass1!"),
            String.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private RegisteredAdmin registerAdmin() {
    String slug = "clinic-" + UUID.randomUUID().toString().substring(0, 8);
    String email = "admin@" + slug + ".example";
    String password = "Str0ngPass!";

    ResponseEntity<String> register =
        restTemplate.postForEntity(
            "/api/v1/auth/register-tenant",
            Map.of(
                "tenantName", "Invite Clinic",
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

  private void inviteUser(
      RegisteredAdmin admin, String email, String role, String temporaryPassword) {
    ResponseEntity<String> invite =
        restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            jsonEntity(
                admin.accessToken(),
                Map.of(
                    "email", email,
                    "fullName", "Invited User",
                    "role", role,
                    "temporaryPassword", temporaryPassword)),
            String.class);
    assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
