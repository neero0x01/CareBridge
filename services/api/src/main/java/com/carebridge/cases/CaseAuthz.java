package com.carebridge.cases;

import com.carebridge.identity.Role;
import java.util.Objects;
import java.util.UUID;

/** Pure domain authorization for Case actions (no Spring). */
public final class CaseAuthz {

  private CaseAuthz() {}

  public static boolean isEligibleAssignee(Role role) {
    return role == Role.REVIEWER;
  }

  public static boolean canClaim(Role role, CaseStatus status, UUID assigneeId) {
    return role == Role.REVIEWER && status == CaseStatus.TO_DO && assigneeId == null;
  }

  public static boolean canAssign(Role actorRole, Role assigneeRole) {
    return actorRole == Role.ORG_ADMIN && isEligibleAssignee(assigneeRole);
  }

  /**
   * Whether the actor may apply a normal status transition (not Claim/Assign entry points).
   *
   * <p>TO_DO → IN_REVIEW is only via Claim or Assign, not this path.
   */
  public static boolean canTransition(
      Role role,
      CaseStatus from,
      CaseStatus to,
      UUID actorId,
      UUID creatorId,
      UUID assigneeId) {
    if (role == Role.AUDITOR) {
      return false;
    }
    if (!CaseWorkflow.isLegalEdge(from, to)) {
      return false;
    }
    // Claim/Assign own TO_DO → IN_REVIEW
    if (from == CaseStatus.TO_DO) {
      return false;
    }
    if (from == CaseStatus.IN_REVIEW) {
      return role == Role.ORG_ADMIN
          || (role == Role.REVIEWER && Objects.equals(actorId, assigneeId));
    }
    if (from == CaseStatus.NEEDS_INFO && to == CaseStatus.IN_REVIEW) {
      return role == Role.ORG_ADMIN
          || Objects.equals(actorId, creatorId)
          || Objects.equals(actorId, assigneeId);
    }
    return false;
  }

  public static boolean canEditFields(
      Role role, CaseStatus status, UUID actorId, UUID creatorId) {
    if (CaseWorkflow.isTerminal(status)) {
      return false;
    }
    if (role == Role.ORG_ADMIN) {
      return !CaseWorkflow.isTerminal(status);
    }
    if (role == Role.CLINICIAN) {
      return status == CaseStatus.TO_DO && Objects.equals(actorId, creatorId);
    }
    return false;
  }
}
