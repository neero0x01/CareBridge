package com.carebridge.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

  boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

  List<User> findByTenantIdOrderByCreatedAtAsc(UUID tenantId);

  Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);
}
