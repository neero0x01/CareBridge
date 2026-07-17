package com.carebridge.webhooks;

import com.carebridge.cases.CaseStatus;
import com.carebridge.cases.CaseType;
import java.util.EnumSet;
import java.util.Set;

/**
 * Open Case rule for lab webhooks: same tenant + patientRef + type {@link CaseType#LAB_FOLLOWUP}
 * + status in {TO_DO, IN_REVIEW, NEEDS_INFO}.
 */
public final class OpenLabCaseMatcher {

  private static final Set<CaseStatus> OPEN =
      EnumSet.of(CaseStatus.TO_DO, CaseStatus.IN_REVIEW, CaseStatus.NEEDS_INFO);

  private OpenLabCaseMatcher() {}

  public static boolean isOpenLabFollowup(CaseType type, CaseStatus status) {
    return type == CaseType.LAB_FOLLOWUP && status != null && OPEN.contains(status);
  }

  public static Set<CaseStatus> openStatuses() {
    return EnumSet.copyOf(OPEN);
  }
}
