package com.carebridge.cases;

import java.util.EnumSet;
import java.util.Set;

/** Pure domain state machine for Case status edges (no Spring, no persistence). */
public final class CaseWorkflow {

  private static final Set<CaseStatus> OPEN =
      EnumSet.of(CaseStatus.TO_DO, CaseStatus.IN_REVIEW, CaseStatus.NEEDS_INFO);

  private CaseWorkflow() {}

  public static boolean isLegalEdge(CaseStatus from, CaseStatus to) {
    if (from == null || to == null || from == to) {
      return false;
    }
    return switch (from) {
      case TO_DO -> to == CaseStatus.IN_REVIEW;
      case IN_REVIEW ->
          to == CaseStatus.NEEDS_INFO
              || to == CaseStatus.APPROVED
              || to == CaseStatus.REJECTED;
      case NEEDS_INFO -> to == CaseStatus.IN_REVIEW;
      case APPROVED, REJECTED -> false;
    };
  }

  public static void requireLegalEdge(CaseStatus from, CaseStatus to) {
    if (!isLegalEdge(from, to)) {
      throw new IllegalTransitionException(from, to);
    }
  }

  public static boolean isTerminal(CaseStatus status) {
    return status == CaseStatus.APPROVED || status == CaseStatus.REJECTED;
  }

  /** Open Case: not terminal (TO_DO, IN_REVIEW, NEEDS_INFO). */
  public static boolean isOpen(CaseStatus status) {
    return status != null && OPEN.contains(status);
  }

  /** Status set for Open Case queries (immutable copy). */
  public static Set<CaseStatus> openStatuses() {
    return EnumSet.copyOf(OPEN);
  }
}
