package com.carebridge.cases;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CaseRepository
    extends JpaRepository<CaseEntity, UUID>, JpaSpecificationExecutor<CaseEntity> {

  Optional<CaseEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
