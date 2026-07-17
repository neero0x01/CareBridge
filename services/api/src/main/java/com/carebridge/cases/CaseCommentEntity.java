package com.carebridge.cases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_comments")
public class CaseCommentEntity {

  @Id private UUID id;

  @Column(name = "case_id", nullable = false)
  private UUID caseId;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CaseCommentEntity() {}

  public CaseCommentEntity(
      UUID id, UUID caseId, UUID tenantId, UUID authorId, String body, Instant createdAt) {
    this.id = id;
    this.caseId = caseId;
    this.tenantId = tenantId;
    this.authorId = authorId;
    this.body = body;
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

  public UUID getAuthorId() {
    return authorId;
  }

  public String getBody() {
    return body;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
