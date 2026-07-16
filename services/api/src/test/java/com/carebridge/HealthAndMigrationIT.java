package com.carebridge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Seam: HTTP API boot + DB (Testcontainers Postgres 16).
 *
 * <p>Asserts the M0 platform skeleton: app starts, Flyway baseline is applied, and
 * actuator health reports UP when the database is available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HealthAndMigrationIT {

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
  }

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void healthIsUpWhenDatabaseIsAvailable() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull().contains("\"status\":\"UP\"");
  }

  @Test
  void flywayBaselineMigrationIsApplied() {
    Integer applied =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE success = true
              AND version = '1'
              AND script = 'V1__baseline.sql'
            """,
            Integer.class);

    assertThat(applied).isEqualTo(1);

    Integer markerRows =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schema_baseline", Integer.class);
    assertThat(markerRows).isEqualTo(1);
  }
}
