package com.carebridge.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Seam: pure domain CaseWorkflow (no Spring). */
class CaseWorkflowTest {

  @ParameterizedTest
  @CsvSource({
    "TO_DO,IN_REVIEW,true",
    "IN_REVIEW,NEEDS_INFO,true",
    "IN_REVIEW,APPROVED,true",
    "IN_REVIEW,REJECTED,true",
    "NEEDS_INFO,IN_REVIEW,true",
    "TO_DO,APPROVED,false",
    "TO_DO,NEEDS_INFO,false",
    "IN_REVIEW,TO_DO,false",
    "APPROVED,IN_REVIEW,false",
    "REJECTED,TO_DO,false",
    "NEEDS_INFO,APPROVED,false",
  })
  void legalEdges(CaseStatus from, CaseStatus to, boolean legal) {
    assertThat(CaseWorkflow.isLegalEdge(from, to)).isEqualTo(legal);
  }

  @Test
  void requireLegalEdgeThrowsOnIllegal() {
    assertThatThrownBy(() -> CaseWorkflow.requireLegalEdge(CaseStatus.APPROVED, CaseStatus.TO_DO))
        .isInstanceOf(IllegalTransitionException.class)
        .hasMessageContaining("APPROVED")
        .hasMessageContaining("TO_DO");
  }

  @Test
  void terminalStatuses() {
    assertThat(CaseWorkflow.isTerminal(CaseStatus.APPROVED)).isTrue();
    assertThat(CaseWorkflow.isTerminal(CaseStatus.REJECTED)).isTrue();
    assertThat(CaseWorkflow.isTerminal(CaseStatus.TO_DO)).isFalse();
    assertThat(CaseWorkflow.isTerminal(CaseStatus.IN_REVIEW)).isFalse();
    assertThat(CaseWorkflow.isTerminal(CaseStatus.NEEDS_INFO)).isFalse();
  }

  @Test
  void openIsComplementOfTerminal() {
    for (CaseStatus status : CaseStatus.values()) {
      assertThat(CaseWorkflow.isOpen(status)).isEqualTo(!CaseWorkflow.isTerminal(status));
    }
  }

  @Test
  void openStatusesListMatchesDomainDefinition() {
    assertThat(CaseWorkflow.openStatuses())
        .containsExactlyInAnyOrder(CaseStatus.TO_DO, CaseStatus.IN_REVIEW, CaseStatus.NEEDS_INFO);
  }
}
