package com.carebridge.audit.dto;

import com.carebridge.audit.AuditLogEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID tenantId,
    UUID actorId,
    String action,
    String entityType,
    UUID entityId,
    JsonNode before,
    JsonNode after,
    String ip,
    Instant createdAt) {

  public static AuditLogResponse from(AuditLogEntity entity, ObjectMapper objectMapper) {
    return new AuditLogResponse(
        entity.getId(),
        entity.getTenantId(),
        entity.getActorId(),
        entity.getAction(),
        entity.getEntityType(),
        entity.getEntityId(),
        parse(entity.getBeforeJson(), objectMapper),
        parse(entity.getAfterJson(), objectMapper),
        entity.getIp(),
        entity.getCreatedAt());
  }

  private static JsonNode parse(String json, ObjectMapper objectMapper) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (Exception ex) {
      return objectMapper.getNodeFactory().textNode(json);
    }
  }
}
