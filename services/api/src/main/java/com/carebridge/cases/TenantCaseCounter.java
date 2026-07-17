package com.carebridge.cases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenant_case_counters")
public class TenantCaseCounter {

  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "next_value", nullable = false)
  private long nextValue;

  protected TenantCaseCounter() {}

  public TenantCaseCounter(UUID tenantId, long nextValue) {
    this.tenantId = tenantId;
    this.nextValue = nextValue;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public long getNextValue() {
    return nextValue;
  }
}
