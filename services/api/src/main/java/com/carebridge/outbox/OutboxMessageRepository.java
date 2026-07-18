package com.carebridge.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, UUID> {

  List<OutboxMessageEntity> findByProcessedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

  List<OutboxMessageEntity> findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
      UUID aggregateId, String eventType);
}
