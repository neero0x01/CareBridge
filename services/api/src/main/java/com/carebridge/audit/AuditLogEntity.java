package com.carebridge.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(nullable = false, length = 100)
  private String action;

  @Column(name = "entity_type", nullable = false, length = 100)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(name = "before_json", columnDefinition = "TEXT")
  private String beforeJson;

  @Column(name = "after_json", columnDefinition = "TEXT")
  private String afterJson;

  @Column(length = 100)
  private String ip;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AuditLogEntity() {}

  public AuditLogEntity(
      UUID id,
      UUID tenantId,
      UUID actorId,
      String action,
      String entityType,
      UUID entityId,
      String beforeJson,
      String afterJson,
      String ip,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.actorId = actorId;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.beforeJson = beforeJson;
    this.afterJson = afterJson;
    this.ip = ip;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getBeforeJson() {
    return beforeJson;
  }

  public String getAfterJson() {
    return afterJson;
  }

  public String getIp() {
    return ip;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
