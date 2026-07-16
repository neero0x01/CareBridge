package com.carebridge.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
}
