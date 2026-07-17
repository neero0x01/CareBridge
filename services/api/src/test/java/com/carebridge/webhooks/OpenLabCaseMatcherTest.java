package com.carebridge.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import com.carebridge.cases.CaseStatus;
import com.carebridge.cases.CaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Seam: pure {@link OpenLabCaseMatcher} (no Spring).
 *
 * <p>Open Case for lab = type LAB_FOLLOWUP + status in {TO_DO, IN_REVIEW, NEEDS_INFO}.
 */
class OpenLabCaseMatcherTest {

  @ParameterizedTest
  @CsvSource({
    "TO_DO,true",
    "IN_REVIEW,true",
    "NEEDS_INFO,true",
    "APPROVED,false",
    "REJECTED,false",
  })
  void labFollowupOpenOnlyForNonTerminalStatuses(CaseStatus status, boolean open) {
    assertThat(OpenLabCaseMatcher.isOpenLabFollowup(CaseType.LAB_FOLLOWUP, status)).isEqualTo(open);
  }

  @ParameterizedTest
  @EnumSource(
      value = CaseType.class,
      names = {"REFERRAL", "PRESCRIPTION_REVIEW", "DISCHARGE", "OTHER"})
  void nonLabTypesAreNeverOpenLabFollowup(CaseType type) {
    assertThat(OpenLabCaseMatcher.isOpenLabFollowup(type, CaseStatus.TO_DO)).isFalse();
    assertThat(OpenLabCaseMatcher.isOpenLabFollowup(type, CaseStatus.IN_REVIEW)).isFalse();
    assertThat(OpenLabCaseMatcher.isOpenLabFollowup(type, CaseStatus.NEEDS_INFO)).isFalse();
  }

  @Test
  void openStatusesListMatchesDomainDefinition() {
    assertThat(OpenLabCaseMatcher.openStatuses())
        .containsExactlyInAnyOrder(CaseStatus.TO_DO, CaseStatus.IN_REVIEW, CaseStatus.NEEDS_INFO);
  }
}
