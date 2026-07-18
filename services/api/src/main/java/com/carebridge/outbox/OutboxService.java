package com.carebridge.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends outbox rows in the <em>current</em> business transaction (transactional outbox). The
 * worker publishes later at-least-once.
 */
@Service
public class OutboxService {

  private final OutboxMessageRepository repository;
  private final ObjectMapper objectMapper;

  public OutboxService(OutboxMessageRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public OutboxMessageEntity enqueue(
      UUID tenantId, String aggregateType, UUID aggregateId, String eventType, Object payload) {
    OutboxMessageEntity message =
        new OutboxMessageEntity(
            UUID.randomUUID(),
            tenantId,
            aggregateType,
            aggregateId,
            eventType,
            toJson(payload),
            Instant.now(),
            null,
            0);
    return repository.save(message);
  }

  private String toJson(Object payload) {
    if (payload == null) {
      return "{}";
    }
    if (payload instanceof String s) {
      return s;
    }
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize outbox payload", ex);
    }
  }
}
