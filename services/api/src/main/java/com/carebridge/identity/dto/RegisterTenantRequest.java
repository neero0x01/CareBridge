package com.carebridge.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterTenantRequest(
    @NotBlank @Size(max = 255) String tenantName,
    @NotBlank
        @Size(min = 2, max = 100)
        @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "must be lowercase alphanumeric with optional hyphens")
        String slug,
    @NotBlank @Email @Size(max = 320) String adminEmail,
    @NotBlank @Size(min = 8, max = 128) String adminPassword,
    @NotBlank @Size(max = 255) String adminFullName) {}
