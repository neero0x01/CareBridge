package com.carebridge.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class AuditLogSpecifications {

  private AuditLogSpecifications() {}

  static Specification<AuditLogEntity> forTenant(UUID tenantId) {
    return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
  }

  static Specification<AuditLogEntity> withEntityType(String entityType) {
    return (root, query, cb) -> cb.equal(root.get("entityType"), entityType);
  }

  static Specification<AuditLogEntity> withEntityId(UUID entityId) {
    return (root, query, cb) -> cb.equal(root.get("entityId"), entityId);
  }

  static Specification<AuditLogEntity> createdFrom(Instant from) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
  }

  static Specification<AuditLogEntity> createdTo(Instant to) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
  }
}
