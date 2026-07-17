package com.carebridge.cases;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantCaseCounterRepository extends JpaRepository<TenantCaseCounter, UUID> {

  /**
   * Atomically allocates the next per-tenant sequence value under a row lock (Postgres upsert).
   *
   * @return the allocated sequence number (used as CB-{n})
   */
  @Query(
      value =
          """
          INSERT INTO tenant_case_counters (tenant_id, next_value)
          VALUES (:tenantId, 1)
          ON CONFLICT (tenant_id) DO UPDATE
          SET next_value = tenant_case_counters.next_value + 1
          RETURNING next_value
          """,
      nativeQuery = true)
  long allocateNext(@Param("tenantId") UUID tenantId);
}
