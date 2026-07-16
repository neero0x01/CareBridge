package com.carebridge.identity;

import com.carebridge.common.error.ApiException;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.identity.dto.InviteUserRequest;
import com.carebridge.identity.dto.PatchUserRequest;
import com.carebridge.identity.dto.UserResponse;
import com.carebridge.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
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
            now);
    userRepository.save(invited);
    return UserResponse.from(invited);
  }

  @Transactional(readOnly = true)
  public List<UserResponse> list(AuthenticatedUser principal) {
    return userRepository.findByTenantIdOrderByCreatedAtAsc(principal.tenantId()).stream()
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

    if (request.role() != null) {
      user.setRole(request.role());
    }
    if (request.active() != null) {
      user.setActive(request.active());
    }

    return UserResponse.from(user);
  }
}
