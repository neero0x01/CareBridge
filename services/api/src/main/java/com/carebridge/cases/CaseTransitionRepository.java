package com.carebridge.cases;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseTransitionRepository extends JpaRepository<CaseTransitionEntity, UUID> {

  List<CaseTransitionEntity> findByCaseIdAndTenantIdOrderByCreatedAtAsc(UUID caseId, UUID tenantId);
}
