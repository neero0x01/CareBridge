package com.carebridge.cases;

public class IllegalTransitionException extends RuntimeException {

  private final CaseStatus from;
  private final CaseStatus to;

  public IllegalTransitionException(CaseStatus from, CaseStatus to) {
    super("Cannot move from " + from + " to " + to);
    this.from = from;
    this.to = to;
  }

  public CaseStatus getFrom() {
    return from;
  }

  public CaseStatus getTo() {
    return to;
  }
}
