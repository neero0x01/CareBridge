package com.carebridge.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
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
 * <p>Happy path for M1 auth: register tenant → login → GET /me.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthRegisterLoginMeIT {

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
  void registerLoginMeHappyPath() {
    String slug = "clinic-" + UUID.randomUUID().toString().substring(0, 8);
    String email = "admin@" + slug + ".example";
    String password = "Str0ngPass!";

    ResponseEntity<String> register =
        restTemplate.postForEntity(
            "/api/v1/auth/register-tenant",
            Map.of(
                "tenantName", "Demo Clinic",
                "slug", slug,
                "adminEmail", email,
                "adminPassword", password,
                "adminFullName", "Ada Admin"),
            String.class);

    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(register.getBody()).isNotNull();

    String accessFromRegister = JsonPath.read(register.getBody(), "$.tokens.accessToken");
    assertThat(accessFromRegister).isNotBlank();
    Integer expiresIn = JsonPath.read(register.getBody(), "$.tokens.expiresIn");
    assertThat(expiresIn).isEqualTo(900);
    assertThat((Object) JsonPath.read(register.getBody(), "$.tenant.slug")).isEqualTo(slug);
    assertThat((Object) JsonPath.read(register.getBody(), "$.user.email")).isEqualTo(email);
    assertThat((Object) JsonPath.read(register.getBody(), "$.user.role")).isEqualTo("ORG_ADMIN");
    assertThat(register.getBody()).doesNotContain(password);
    assertThat(register.getBody()).doesNotContain("passwordHash");

    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", slug,
                "email", email,
                "password", password),
            String.class);

    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(login.getBody()).isNotNull();
    String accessToken = JsonPath.read(login.getBody(), "$.accessToken");
    assertThat(accessToken).isNotBlank();
    Integer loginExpiresIn = JsonPath.read(login.getBody(), "$.expiresIn");
    assertThat(loginExpiresIn).isEqualTo(900);

    try {
      SignedJWT jwt = SignedJWT.parse(accessToken);
      assertThat(jwt.getJWTClaimsSet().getSubject()).isNotBlank();
      assertThat(jwt.getJWTClaimsSet().getStringClaim("tenant_id")).isNotBlank();
      assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("ORG_ADMIN");
      assertThat(jwt.getJWTClaimsSet().getStringClaim("email")).isEqualTo(email);
      long ttlSeconds =
          (jwt.getJWTClaimsSet().getExpirationTime().getTime()
                  - jwt.getJWTClaimsSet().getIssueTime().getTime())
              / 1000;
      assertThat(ttlSeconds).isEqualTo(900);
    } catch (java.text.ParseException e) {
      throw new AssertionError("access token is not a parseable JWT", e);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

    ResponseEntity<String> me =
        restTemplate.exchange(
            "/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody()).isNotNull();
    assertThat((Object) JsonPath.read(me.getBody(), "$.user.email")).isEqualTo(email);
    assertThat((Object) JsonPath.read(me.getBody(), "$.user.role")).isEqualTo("ORG_ADMIN");
    assertThat((Object) JsonPath.read(me.getBody(), "$.user.fullName")).isEqualTo("Ada Admin");
    assertThat((Object) JsonPath.read(me.getBody(), "$.tenant.slug")).isEqualTo(slug);
    assertThat((Object) JsonPath.read(me.getBody(), "$.tenant.name")).isEqualTo("Demo Clinic");
    assertThat(me.getBody()).doesNotContain("password");
  }

  @Test
  void meWithoutTokenIsUnauthorized() {
    ResponseEntity<String> me = restTemplate.getForEntity("/api/v1/me", String.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void loginWithWrongPasswordIsUnauthorized() {
    String slug = "clinic-" + UUID.randomUUID().toString().substring(0, 8);
    String email = "admin@" + slug + ".example";

    ResponseEntity<String> register =
        restTemplate.postForEntity(
            "/api/v1/auth/register-tenant",
            Map.of(
                "tenantName", "Other Clinic",
                "slug", slug,
                "adminEmail", email,
                "adminPassword", "Str0ngPass!",
                "adminFullName", "Bob Admin"),
            String.class);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<String> login =
        restTemplate.postForEntity(
            "/api/v1/auth/login",
            Map.of(
                "tenantSlug", slug,
                "email", email,
                "password", "wrong-password"),
            String.class);

    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
