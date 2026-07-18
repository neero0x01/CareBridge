package com.carebridge.audit;

/**
 * Canonical action and entity-type strings for audit rows ({@link AuditLogEntity#getAction()},
 * {@link AuditLogEntity#getEntityType()}).
 */
public final class AuditActions {

  public static final String CASE_CREATED = "CASE_CREATED";
  public static final String CASE_UPDATED = "CASE_UPDATED";
  public static final String CASE_TRANSITIONED = "CASE_TRANSITIONED";
  public static final String CASE_COMMENT_ADDED = "CASE_COMMENT_ADDED";
  public static final String USER_INVITED = "USER_INVITED";
  public static final String USER_UPDATED = "USER_UPDATED";

  /** Domain entity type labels (filterable via {@code entityType} query param). */
  public static final String ENTITY_CASE = "Case";

  public static final String ENTITY_USER = "User";

  private AuditActions() {}
}
