package com.carebridge.cases.dto;

import com.carebridge.cases.CasePriority;
import com.carebridge.cases.CaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCaseRequest(
    @NotBlank @Size(max = 500) String title,
    @NotNull CaseType type,
    @NotNull CasePriority priority,
    @NotBlank @Size(max = 255) String patientDisplayName,
    @NotBlank @Size(max = 100) String patientRef,
    @Size(max = 10000) String description) {}
