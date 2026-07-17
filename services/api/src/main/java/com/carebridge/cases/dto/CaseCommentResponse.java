package com.carebridge.cases.dto;

import com.carebridge.cases.CaseCommentEntity;
import java.time.Instant;
import java.util.UUID;

public record CaseCommentResponse(
    UUID id, UUID caseId, UUID authorId, String body, Instant createdAt) {

  public static CaseCommentResponse from(CaseCommentEntity entity) {
    return new CaseCommentResponse(
        entity.getId(),
        entity.getCaseId(),
        entity.getAuthorId(),
        entity.getBody(),
        entity.getCreatedAt());
  }
}
