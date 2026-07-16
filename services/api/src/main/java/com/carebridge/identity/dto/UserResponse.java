package com.carebridge.identity.dto;

import com.carebridge.identity.Role;
import com.carebridge.identity.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    UUID tenantId,
    String email,
    String fullName,
    Role role,
    boolean active,
    boolean mustChangePassword,
    Instant createdAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getTenantId(),
        user.getEmail(),
        user.getFullName(),
        user.getRole(),
        user.isActive(),
        user.isMustChangePassword(),
        user.getCreatedAt());
  }
}
