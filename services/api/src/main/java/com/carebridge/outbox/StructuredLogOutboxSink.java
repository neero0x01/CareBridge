package com.carebridge.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * v1 outbox sink: structured application logs (no required message broker). Consumers can scrape
 * {@code event=domain_event} lines; at-least-once means duplicates are possible.
 */
@Component
public class StructuredLogOutboxSink implements OutboxSink {

  private static final Logger log = LoggerFactory.getLogger(StructuredLogOutboxSink.class);

  private final ObjectMapper objectMapper;

  public StructuredLogOutboxSink(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void publish(OutboxMessageEntity message) {
    try {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("event", "domain_event");
      node.put("outboxId", message.getId().toString());
      node.put("tenantId", message.getTenantId().toString());
      node.put("aggregateType", message.getAggregateType());
      node.put("aggregateId", message.getAggregateId().toString());
      node.put("eventType", message.getEventType());
      node.put("attempts", message.getAttempts());
      node.put("createdAt", message.getCreatedAt().toString());
      // Embed payload as raw JSON value when possible; fall back to string.
      try {
        node.set("payload", objectMapper.readTree(message.getPayloadJson()));
      } catch (JsonProcessingException ex) {
        node.put("payload", message.getPayloadJson());
      }
      log.info("{}", objectMapper.writeValueAsString(node));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to format outbox log line", ex);
    }
  }
}
