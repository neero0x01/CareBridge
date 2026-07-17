package com.carebridge.cases;

import static org.assertj.core.api.Assertions.assertThat;

import com.carebridge.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Seam: pure domain CaseAuthz (no Spring). */
class CaseAuthzTest {

  private final UUID reviewerId = UUID.randomUUID();
  private final UUID otherReviewerId = UUID.randomUUID();
  private final UUID creatorId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();

  @Test
  void claimOnlyReviewerOnUnassignedToDo() {
    assertThat(CaseAuthz.canClaim(Role.REVIEWER, CaseStatus.TO_DO, null)).isTrue();
    assertThat(CaseAuthz.canClaim(Role.REVIEWER, CaseStatus.TO_DO, reviewerId)).isFalse();
    assertThat(CaseAuthz.canClaim(Role.REVIEWER, CaseStatus.IN_REVIEW, null)).isFalse();
    assertThat(CaseAuthz.canClaim(Role.ORG_ADMIN, CaseStatus.TO_DO, null)).isFalse();
    assertThat(CaseAuthz.canClaim(Role.CLINICIAN, CaseStatus.TO_DO, null)).isFalse();
    assertThat(CaseAuthz.canClaim(Role.AUDITOR, CaseStatus.TO_DO, null)).isFalse();
  }

  @Test
  void assignRequiresOrgAdminAndReviewerAssignee() {
    assertThat(CaseAuthz.canAssign(Role.ORG_ADMIN, Role.REVIEWER)).isTrue();
    assertThat(CaseAuthz.canAssign(Role.ORG_ADMIN, Role.CLINICIAN)).isFalse();
    assertThat(CaseAuthz.canAssign(Role.REVIEWER, Role.REVIEWER)).isFalse();
    assertThat(CaseAuthz.isEligibleAssignee(Role.REVIEWER)).isTrue();
    assertThat(CaseAuthz.isEligibleAssignee(Role.CLINICIAN)).isFalse();
  }

  @Test
  void inReviewTransitionsOnlyAssigneeOrAdmin() {
    assertThat(
            CaseAuthz.canTransition(
                Role.REVIEWER,
                CaseStatus.IN_REVIEW,
                CaseStatus.APPROVED,
                reviewerId,
                creatorId,
                reviewerId))
        .isTrue();
    assertThat(
            CaseAuthz.canTransition(
                Role.ORG_ADMIN,
                CaseStatus.IN_REVIEW,
                CaseStatus.NEEDS_INFO,
                adminId,
                creatorId,
                reviewerId))
        .isTrue();
    assertThat(
            CaseAuthz.canTransition(
                Role.REVIEWER,
                CaseStatus.IN_REVIEW,
                CaseStatus.APPROVED,
                otherReviewerId,
                creatorId,
                reviewerId))
        .isFalse();
    assertThat(
            CaseAuthz.canTransition(
                Role.CLINICIAN,
                CaseStatus.IN_REVIEW,
                CaseStatus.APPROVED,
                creatorId,
                creatorId,
                reviewerId))
        .isFalse();
    assertThat(
            CaseAuthz.canTransition(
                Role.AUDITOR,
                CaseStatus.IN_REVIEW,
                CaseStatus.APPROVED,
                adminId,
                creatorId,
                reviewerId))
        .isFalse();
  }

  @Test
  void resubmitNeedsInfoByCreatorAssigneeOrAdmin() {
    assertThat(
            CaseAuthz.canTransition(
                Role.CLINICIAN,
                CaseStatus.NEEDS_INFO,
                CaseStatus.IN_REVIEW,
                creatorId,
                creatorId,
                reviewerId))
        .isTrue();
    assertThat(
            CaseAuthz.canTransition(
                Role.REVIEWER,
                CaseStatus.NEEDS_INFO,
                CaseStatus.IN_REVIEW,
                reviewerId,
                creatorId,
                reviewerId))
        .isTrue();
    assertThat(
            CaseAuthz.canTransition(
                Role.ORG_ADMIN,
                CaseStatus.NEEDS_INFO,
                CaseStatus.IN_REVIEW,
                adminId,
                creatorId,
                reviewerId))
        .isTrue();
    assertThat(
            CaseAuthz.canTransition(
                Role.CLINICIAN,
                CaseStatus.NEEDS_INFO,
                CaseStatus.IN_REVIEW,
                UUID.randomUUID(),
                creatorId,
                reviewerId))
        .isFalse();
  }

  @Test
  void clinicianEditsOnlyAsCreatorWhileToDo() {
    assertThat(CaseAuthz.canEditFields(Role.CLINICIAN, CaseStatus.TO_DO, creatorId, creatorId))
        .isTrue();
    assertThat(
            CaseAuthz.canEditFields(
                Role.CLINICIAN, CaseStatus.TO_DO, UUID.randomUUID(), creatorId))
        .isFalse();
    assertThat(CaseAuthz.canEditFields(Role.CLINICIAN, CaseStatus.IN_REVIEW, creatorId, creatorId))
        .isFalse();
    assertThat(CaseAuthz.canEditFields(Role.ORG_ADMIN, CaseStatus.TO_DO, adminId, creatorId))
        .isTrue();
    assertThat(CaseAuthz.canEditFields(Role.ORG_ADMIN, CaseStatus.APPROVED, adminId, creatorId))
        .isFalse();
    assertThat(CaseAuthz.canEditFields(Role.REVIEWER, CaseStatus.TO_DO, reviewerId, creatorId))
        .isFalse();
  }

  @Test
  void commentAllowedForNonAuditorOnNonTerminalOnly() {
    assertThat(CaseAuthz.canComment(Role.ORG_ADMIN, CaseStatus.TO_DO)).isTrue();
    assertThat(CaseAuthz.canComment(Role.CLINICIAN, CaseStatus.IN_REVIEW)).isTrue();
    assertThat(CaseAuthz.canComment(Role.REVIEWER, CaseStatus.NEEDS_INFO)).isTrue();
    assertThat(CaseAuthz.canComment(Role.AUDITOR, CaseStatus.TO_DO)).isFalse();
    assertThat(CaseAuthz.canComment(Role.ORG_ADMIN, CaseStatus.APPROVED)).isFalse();
    assertThat(CaseAuthz.canComment(Role.CLINICIAN, CaseStatus.REJECTED)).isFalse();
    assertThat(CaseAuthz.canComment(Role.REVIEWER, CaseStatus.APPROVED)).isFalse();
  }
}
