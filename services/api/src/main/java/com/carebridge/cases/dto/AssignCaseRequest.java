package com.carebridge.cases.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignCaseRequest(@NotNull UUID assigneeId, @NotNull Long version) {}
