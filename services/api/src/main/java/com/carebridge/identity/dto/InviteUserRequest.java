package com.carebridge.identity.dto;

import com.carebridge.identity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 255) String fullName,
    @NotNull Role role,
    @NotBlank @Size(min = 8, max = 128) String temporaryPassword) {}
