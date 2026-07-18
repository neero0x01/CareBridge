package com.carebridge.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carebridge.config.CarebridgeProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Seam: {@link OutboxWorker#processPending()} against repository + sink (no Spring context).
 *
 * <p>MUH-15: worker publishes and sets processed_at / attempts (at-least-once).
 */
@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

  @Mock private OutboxMessageRepository repository;
  @Mock private OutboxSink sink;

  private CarebridgeProperties properties;
  private OutboxWorker worker;
  private final List<OutboxMessageEntity> saved = new ArrayList<>();

  @BeforeEach
  void setUp() {
    properties = new CarebridgeProperties();
    properties.getOutbox().setSchedulingEnabled(false);
    properties.getOutbox().setBatchSize(50);
    worker = new OutboxWorker(repository, sink, properties);
    lenient()
        .when(repository.save(any(OutboxMessageEntity.class)))
        .thenAnswer(
            inv -> {
              OutboxMessageEntity m = inv.getArgument(0);
              saved.add(m);
              return m;
            });
  }

  @Test
  void processPendingPublishesAndMarksProcessed() {
    OutboxMessageEntity msg = unprocessed("case.transitioned");
    when(repository.findByProcessedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
        .thenReturn(List.of(msg));

    int n = worker.processPending();

    assertThat(n).isEqualTo(1);
    verify(sink, times(1)).publish(msg);
    assertThat(msg.getProcessedAt()).isNotNull();
    assertThat(msg.getAttempts()).isEqualTo(1);
    assertThat(saved).contains(msg);
  }

  @Test
  void processPendingOnSinkFailureIncrementsAttemptsButLeavesUnprocessed() {
    OutboxMessageEntity msg = unprocessed("case.created");
    when(repository.findByProcessedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
        .thenReturn(List.of(msg));
    doThrow(new RuntimeException("sink down")).when(sink).publish(msg);

    int n = worker.processPending();

    assertThat(n).isEqualTo(0);
    assertThat(msg.getProcessedAt()).isNull();
    assertThat(msg.getAttempts()).isEqualTo(1);
    verify(repository).save(msg);
  }

  @Test
  void scheduledPollSkippedWhenSchedulingDisabled() {
    worker.scheduledPoll();

    verify(repository, never()).findByProcessedAtIsNullOrderByCreatedAtAsc(any(Pageable.class));
    verify(sink, never()).publish(any());
  }

  @Test
  void processPendingEmptyBatchReturnsZero() {
    when(repository.findByProcessedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
        .thenReturn(List.of());

    assertThat(worker.processPending()).isEqualTo(0);
    verify(sink, never()).publish(any());
  }

  @Test
  void processPendingUsesConfiguredBatchSize() {
    properties.getOutbox().setBatchSize(10);
    when(repository.findByProcessedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
        .thenReturn(List.of());

    worker.processPending();

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findByProcessedAtIsNullOrderByCreatedAtAsc(pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
  }

  private static OutboxMessageEntity unprocessed(String eventType) {
    return new OutboxMessageEntity(
        UUID.randomUUID(),
        UUID.randomUUID(),
        DomainEventTypes.AGGREGATE_CASE,
        UUID.randomUUID(),
        eventType,
        "{\"ok\":true}",
        Instant.parse("2026-01-01T00:00:00Z"),
        null,
        0);
  }
}
