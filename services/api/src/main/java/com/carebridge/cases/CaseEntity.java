package com.carebridge.cases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cases")
public class CaseEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "case_number", nullable = false, length = 50)
  private String caseNumber;

  @Column(nullable = false, length = 500)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private CaseType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private CasePriority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private CaseStatus status;

  @Column(name = "patient_display_name", nullable = false)
  private String patientDisplayName;

  @Column(name = "patient_ref", nullable = false, length = 100)
  private String patientRef;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "assignee_id")
  private UUID assigneeId;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CaseEntity() {}

  public CaseEntity(
      UUID id,
      UUID tenantId,
      String caseNumber,
      String title,
      CaseType type,
      CasePriority priority,
      CaseStatus status,
      String patientDisplayName,
      String patientRef,
      String description,
      UUID createdBy,
      UUID assigneeId,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.caseNumber = caseNumber;
    this.title = title;
    this.type = type;
    this.priority = priority;
    this.status = status;
    this.patientDisplayName = patientDisplayName;
    this.patientRef = patientRef;
    this.description = description;
    this.createdBy = createdBy;
    this.assigneeId = assigneeId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getCaseNumber() {
    return caseNumber;
  }

  public String getTitle() {
    return title;
  }

  public CaseType getType() {
    return type;
  }

  public CasePriority getPriority() {
    return priority;
  }

  public CaseStatus getStatus() {
    return status;
  }

  public String getPatientDisplayName() {
    return patientDisplayName;
  }

  public String getPatientRef() {
    return patientRef;
  }

  public String getDescription() {
    return description;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public UUID getAssigneeId() {
    return assigneeId;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setPriority(CasePriority priority) {
    this.priority = priority;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setAssigneeId(UUID assigneeId) {
    this.assigneeId = assigneeId;
  }

  public void setStatus(CaseStatus status) {
    this.status = status;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
