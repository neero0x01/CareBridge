package com.carebridge.identity;

import com.carebridge.audit.AuditActions;
import com.carebridge.audit.AuditService;
import com.carebridge.common.error.ApiException;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.identity.dto.InviteUserRequest;
import com.carebridge.identity.dto.PatchUserRequest;
import com.carebridge.identity.dto.UserResponse;
import com.carebridge.outbox.DomainEventTypes;
import com.carebridge.outbox.OutboxService;
import com.carebridge.security.AuthenticatedUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditService auditService;
  private final OutboxService outboxService;

  public UserService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      AuditService auditService,
      OutboxService outboxService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditService = auditService;
    this.outboxService = outboxService;
  }

  @Transactional
  public UserResponse invite(AuthenticatedUser principal, InviteUserRequest request) {
    String email = AuthService.normalizeEmail(request.email());
    if (userRepository.existsByTenantIdAndEmailIgnoreCase(principal.tenantId(), email)) {
      throw new ApiException(
          ErrorCode.CONFLICT, HttpStatus.CONFLICT, "User with this email already exists");
    }

    Instant now = Instant.now();
    User invited =
        new User(
            UUID.randomUUID(),
            principal.tenantId(),
            email,
            passwordEncoder.encode(request.temporaryPassword()),
            request.fullName().trim(),
            request.role(),
            true,
            true,
            false,
            now);
    userRepository.save(invited);
    auditService.record(
        principal.tenantId(),
        principal.userId(),
        AuditActions.USER_INVITED,
        AuditActions.ENTITY_USER,
        invited.getId(),
        null,
        userSnapshot(invited));
    Map<String, Object> payload = userSnapshot(invited);
    payload.put("actorId", principal.userId().toString());
    outboxService.enqueue(
        principal.tenantId(),
        DomainEventTypes.AGGREGATE_USER,
        invited.getId(),
        DomainEventTypes.USER_INVITED,
        payload);
    return UserResponse.from(invited);
  }

  @Transactional(readOnly = true)
  public List<UserResponse> list(AuthenticatedUser principal) {
    return userRepository.findByTenantIdAndSystemFalseOrderByCreatedAtAsc(principal.tenantId())
        .stream()
        .map(UserResponse::from)
        .toList();
  }

  @Transactional
  public UserResponse patch(AuthenticatedUser principal, UUID userId, PatchUserRequest request) {
    User user =
        userRepository
            .findByIdAndTenantId(userId, principal.tenantId())
            .orElseThrow(
                () -> new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));

    Map<String, Object> before = userSnapshot(user);
    if (user.isSystem()) {
      throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "User not found");
    }

    if (request.role() != null) {
      user.setRole(request.role());
    }
    if (request.active() != null) {
      user.setActive(request.active());
    }

    auditService.record(
        principal.tenantId(),
        principal.userId(),
        AuditActions.USER_UPDATED,
        AuditActions.ENTITY_USER,
        user.getId(),
        before,
        userSnapshot(user));
    return UserResponse.from(user);
  }

  /** Snapshot of User fields for audit (never includes password hash). */
  static Map<String, Object> userSnapshot(User user) {
    Map<String, Object> snap = new LinkedHashMap<>();
    snap.put("id", user.getId().toString());
    snap.put("email", user.getEmail());
    snap.put("fullName", user.getFullName());
    snap.put("role", user.getRole().name());
    snap.put("active", user.isActive());
    snap.put("mustChangePassword", user.isMustChangePassword());
    return snap;
  }
}
