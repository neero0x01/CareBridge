package com.carebridge.webhooks;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository
    extends JpaRepository<WebhookEventEntity, WebhookEventEntity.Pk> {

  Optional<WebhookEventEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
