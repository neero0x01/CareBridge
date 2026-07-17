package com.carebridge.cases;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class CaseSpecifications {

  private CaseSpecifications() {}

  static Specification<CaseEntity> forTenant(UUID tenantId) {
    return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
  }

  static Specification<CaseEntity> withStatus(CaseStatus status) {
    return (root, query, cb) -> cb.equal(root.get("status"), status);
  }

  static Specification<CaseEntity> withPriority(CasePriority priority) {
    return (root, query, cb) -> cb.equal(root.get("priority"), priority);
  }

  static Specification<CaseEntity> withAssignee(UUID assigneeId) {
    return (root, query, cb) -> cb.equal(root.get("assigneeId"), assigneeId);
  }

  static Specification<CaseEntity> unassigned() {
    return (root, query, cb) -> cb.isNull(root.get("assigneeId"));
  }

  static Specification<CaseEntity> matchingQuery(String q) {
    String pattern = "%" + q.toLowerCase() + "%";
    return (root, query, cb) ->
        cb.or(
            cb.like(cb.lower(root.get("title")), pattern),
            cb.like(cb.lower(root.get("patientDisplayName")), pattern),
            cb.like(cb.lower(root.get("patientRef")), pattern),
            cb.like(cb.lower(root.get("caseNumber")), pattern),
            cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern));
  }
}
