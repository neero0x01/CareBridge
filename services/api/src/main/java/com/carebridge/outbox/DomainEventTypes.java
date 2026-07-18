package com.carebridge.outbox;

/**
 * Canonical domain-event type strings for {@link OutboxMessageEntity#getEventType()}.
 *
 * <p>v1 catalog (MUH-15): case.created, case.transitioned, case.assigned, user.invited,
 * webhook.processed.
 */
public final class DomainEventTypes {

  public static final String CASE_CREATED = "case.created";
  public static final String CASE_TRANSITIONED = "case.transitioned";
  public static final String CASE_ASSIGNED = "case.assigned";
  public static final String USER_INVITED = "user.invited";
  public static final String WEBHOOK_PROCESSED = "webhook.processed";

  public static final String AGGREGATE_CASE = "Case";
  public static final String AGGREGATE_USER = "User";
  public static final String AGGREGATE_WEBHOOK_EVENT = "WebhookEvent";

  private DomainEventTypes() {}
}
