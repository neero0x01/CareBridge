package com.carebridge.cases.dto;

import jakarta.validation.constraints.NotNull;

public record ClaimCaseRequest(@NotNull Long version) {}
