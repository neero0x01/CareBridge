package com.carebridge.outbox;

/** Destination for published domain events (v1: structured application logs). */
public interface OutboxSink {

  void publish(OutboxMessageEntity message);
}
