package com.carebridge.cases;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseCommentRepository extends JpaRepository<CaseCommentEntity, UUID> {

  List<CaseCommentEntity> findByCaseIdAndTenantIdOrderByCreatedAtAsc(UUID caseId, UUID tenantId);
}
