package com.carebridge.cases.dto;

import com.carebridge.cases.CaseStatus;
import com.carebridge.cases.CaseTransitionEntity;
import java.time.Instant;
import java.util.UUID;

public record CaseTransitionResponse(
    UUID id,
    UUID caseId,
    CaseStatus fromStatus,
    CaseStatus toStatus,
    UUID actorId,
    String comment,
    Instant createdAt) {

  public static CaseTransitionResponse from(CaseTransitionEntity entity) {
    return new CaseTransitionResponse(
        entity.getId(),
        entity.getCaseId(),
        entity.getFromStatus(),
        entity.getToStatus(),
        entity.getActorId(),
        entity.getComment(),
        entity.getCreatedAt());
  }
}
