package com.carebridge.audit;

import com.carebridge.audit.dto.AuditLogResponse;
import com.carebridge.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  /** Append an audit row in the current transaction (no update/delete API is exposed). */
  @Transactional
  public void record(
      UUID tenantId,
      UUID actorId,
      String action,
      String entityType,
      UUID entityId,
      Object before,
      Object after) {
    auditLogRepository.save(
        new AuditLogEntity(
            UUID.randomUUID(),
            tenantId,
            actorId,
            action,
            entityType,
            entityId,
            toJson(before),
            toJson(after),
            null,
            Instant.now()));
  }

  @Transactional(readOnly = true)
  public Page<AuditLogResponse> list(
      AuthenticatedUser principal,
      String entityType,
      UUID entityId,
      Instant from,
      Instant to,
      Pageable pageable) {
    Specification<AuditLogEntity> spec = AuditLogSpecifications.forTenant(principal.tenantId());

    if (entityType != null && !entityType.isBlank()) {
      spec = spec.and(AuditLogSpecifications.withEntityType(entityType.trim()));
    }
    if (entityId != null) {
      spec = spec.and(AuditLogSpecifications.withEntityId(entityId));
    }
    if (from != null) {
      spec = spec.and(AuditLogSpecifications.createdFrom(from));
    }
    if (to != null) {
      spec = spec.and(AuditLogSpecifications.createdTo(to));
    }

    return auditLogRepository
        .findAll(spec, pageable)
        .map(entity -> AuditLogResponse.from(entity, objectMapper));
  }

  private String toJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize audit payload", ex);
    }
  }
}
