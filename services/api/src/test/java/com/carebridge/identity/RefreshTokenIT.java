package com.carebridge.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Seam: HTTP API {@code /api/v1} (Testcontainers Postgres).
 *
 * <p>MUH-9: refresh token rotate-on-use and family revoke on reuse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RefreshTokenIT {

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
  void loginIssuesRefreshAndRefreshRotatesTokens() {
    RegisteredAdmin admin = registerAdmin();

    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", admin.slug(),
                "email", admin.email(),
                "password", admin.password()),
            String.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login.getBody()).isNotNull();

    String access1 = JsonPath.read(login.getBody(), "$.accessToken");
    String refresh1 = JsonPath.read(login.getBody(), "$.refreshToken");
    Integer expiresIn = JsonPath.read(login.getBody(), "$.expiresIn");
    assertThat(access1).isNotBlank();
    assertThat(refresh1).isNotBlank();
    assertThat(expiresIn).isEqualTo(900);

    ResponseEntity<String> refreshed =
        restTemplate.postForEntity(
            "/api/v1/auth/refresh",
            Map.of("refreshToken", refresh1),
            String.class);
    assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(refreshed.getBody()).isNotNull();

    String access2 = JsonPath.read(refreshed.getBody(), "$.accessToken");
    String refresh2 = JsonPath.read(refreshed.getBody(), "$.refreshToken");
    assertThat(access2).isNotBlank().isNotEqualTo(access1);
    assertThat(refresh2).isNotBlank().isNotEqualTo(refresh1);
    Integer refreshExpiresIn = JsonPath.read(refreshed.getBody(), "$.expiresIn");
    assertThat(refreshExpiresIn).isEqualTo(900);
  }

  @Test
  void reuseOfRevokedRefreshTokenRevokesFamily() {
    RegisteredAdmin admin = registerAdmin();

    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", admin.slug(),
                "email", admin.email(),
                "password", admin.password()),
            String.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    String refresh1 = JsonPath.read(login.getBody(), "$.refreshToken");

    ResponseEntity<String> firstRefresh =
        restTemplate.postForEntity(
            "/api/v1/auth/refresh", Map.of("refreshToken", refresh1), String.class);
    assertThat(firstRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
    String refresh2 = JsonPath.read(firstRefresh.getBody(), "$.refreshToken");

    // Replay the already-rotated (revoked) token → family revoke
    ResponseEntity<String> reuse =
        restTemplate.postForEntity(
            "/api/v1/auth/refresh", Map.of("refreshToken", refresh1), String.class);
    assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(reuse.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(reuse.getBody(), "$.code")).isEqualTo("UNAUTHORIZED");

    // Sibling token in the same family must also fail
    ResponseEntity<String> afterReuse =
        restTemplate.postForEntity(
            "/api/v1/auth/refresh", Map.of("refreshToken", refresh2), String.class);
    assertThat(afterReuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private RegisteredAdmin registerAdmin() {
    String slug = "clinic-" + UUID.randomUUID().toString().substring(0, 8);
    String email = "admin@" + slug + ".example";
    String password = "Str0ngPass!";

    ResponseEntity<String> register =
        restTemplate.postForEntity(
            "/api/v1/auth/register-tenant",
            Map.of(
                "tenantName", "Refresh Clinic",
                "slug", slug,
                "adminEmail", email,
                "adminPassword", password,
                "adminFullName", "Ada Admin"),
            String.class);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(register.getBody()).isNotNull();
    String refreshFromRegister = JsonPath.read(register.getBody(), "$.tokens.refreshToken");
    assertThat(refreshFromRegister).isNotBlank();
    return new RegisteredAdmin(slug, email, password);
  }

  private record RegisteredAdmin(String slug, String email, String password) {}
}
