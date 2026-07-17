package com.carebridge.cases.dto;

import com.carebridge.cases.CaseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionCaseRequest(
    @NotNull CaseStatus toStatus,
    @Size(max = 5000) String comment,
    @NotNull Long version) {}
