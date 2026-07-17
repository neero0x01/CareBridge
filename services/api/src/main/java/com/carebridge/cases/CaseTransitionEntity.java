package com.carebridge.cases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_transitions")
public class CaseTransitionEntity {

  @Id private UUID id;

  @Column(name = "case_id", nullable = false)
  private UUID caseId;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", nullable = false, length = 50)
  private CaseStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 50)
  private CaseStatus toStatus;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(columnDefinition = "TEXT")
  private String comment;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CaseTransitionEntity() {}

  public CaseTransitionEntity(
      UUID id,
      UUID caseId,
      UUID tenantId,
      CaseStatus fromStatus,
      CaseStatus toStatus,
      UUID actorId,
      String comment,
      Instant createdAt) {
    this.id = id;
    this.caseId = caseId;
    this.tenantId = tenantId;
    this.fromStatus = fromStatus;
    this.toStatus = toStatus;
    this.actorId = actorId;
    this.comment = comment;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCaseId() {
    return caseId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public CaseStatus getFromStatus() {
    return fromStatus;
  }

  public CaseStatus getToStatus() {
    return toStatus;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getComment() {
    return comment;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
