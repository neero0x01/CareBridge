package com.carebridge.cases.dto;

import com.carebridge.cases.CaseEntity;
import com.carebridge.cases.CasePriority;
import com.carebridge.cases.CaseStatus;
import com.carebridge.cases.CaseType;
import java.time.Instant;
import java.util.UUID;

public record CaseResponse(
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
    long version,
    Instant createdAt,
    Instant updatedAt) {

  public static CaseResponse from(CaseEntity entity) {
    return new CaseResponse(
        entity.getId(),
        entity.getTenantId(),
        entity.getCaseNumber(),
        entity.getTitle(),
        entity.getType(),
        entity.getPriority(),
        entity.getStatus(),
        entity.getPatientDisplayName(),
        entity.getPatientRef(),
        entity.getDescription(),
        entity.getCreatedBy(),
        entity.getAssigneeId(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
