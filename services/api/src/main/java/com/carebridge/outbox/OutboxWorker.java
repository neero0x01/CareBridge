package com.carebridge.outbox;

import com.carebridge.config.CarebridgeProperties;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls unprocessed outbox rows, publishes to the configured {@link OutboxSink}, and marks them
 * processed (at-least-once delivery).
 */
@Component
public class OutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

  private final OutboxMessageRepository repository;
  private final OutboxSink sink;
  private final CarebridgeProperties properties;

  public OutboxWorker(
      OutboxMessageRepository repository, OutboxSink sink, CarebridgeProperties properties) {
    this.repository = repository;
    this.sink = sink;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${carebridge.outbox.poll-interval-ms:2000}")
  public void scheduledPoll() {
    if (!properties.getOutbox().isSchedulingEnabled()) {
      return;
    }
    processPending();
  }

  /**
   * Process up to the configured batch of unprocessed messages. Returns how many were marked
   * processed. Safe to call from tests and the scheduler.
   */
  @Transactional
  public int processPending() {
    int batchSize = properties.getOutbox().getBatchSize();
    List<OutboxMessageEntity> batch =
        repository.findByProcessedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, batchSize));
    int processed = 0;
    for (OutboxMessageEntity message : batch) {
      if (processOne(message)) {
        processed++;
      }
    }
    return processed;
  }

  private boolean processOne(OutboxMessageEntity message) {
    message.setAttempts(message.getAttempts() + 1);
    try {
      sink.publish(message);
      message.setProcessedAt(Instant.now());
      repository.save(message);
      return true;
    } catch (RuntimeException ex) {
      repository.save(message);
      log.warn(
          "Outbox publish failed outboxId={} eventType={} attempts={}: {}",
          message.getId(),
          message.getEventType(),
          message.getAttempts(),
          ex.toString());
      return false;
    }
  }
}
