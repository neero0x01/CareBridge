package com.carebridge.cases.dto;

import com.carebridge.cases.CasePriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PatchCaseRequest(
    @Size(max = 500) String title,
    @Size(max = 10000) String description,
    CasePriority priority,
    @NotNull Long version) {}
