package com.carebridge.identity;

import com.carebridge.common.error.ApiException;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.config.CarebridgeProperties;
import com.carebridge.identity.dto.ChangePasswordRequest;
import com.carebridge.identity.dto.LoginRequest;
import com.carebridge.identity.dto.MeResponse;
import com.carebridge.identity.dto.RegisterTenantRequest;
import com.carebridge.identity.dto.RegisterTenantResponse;
import com.carebridge.identity.dto.TenantResponse;
import com.carebridge.identity.dto.TokenResponse;
import com.carebridge.identity.dto.UserResponse;
import com.carebridge.security.AuthenticatedUser;
import com.carebridge.security.JwtService;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final CarebridgeProperties properties;

  public AuthService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      CarebridgeProperties properties) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.properties = properties;
  }

  @Transactional
  public RegisterTenantResponse registerTenant(RegisterTenantRequest request) {
    if (!properties.getAuth().isRegisterTenantEnabled()) {
      throw new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Not found");
    }

    String slug = request.slug().toLowerCase(Locale.ROOT).trim();
    if (tenantRepository.existsBySlug(slug)) {
      throw new ApiException(
          ErrorCode.CONFLICT, HttpStatus.CONFLICT, "Tenant slug already exists");
    }

    Instant now = Instant.now();
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant(tenantId, request.tenantName().trim(), slug, now);
    tenantRepository.save(tenant);

    String email = normalizeEmail(request.adminEmail());
    String passwordHash = passwordEncoder.encode(request.adminPassword());
    User admin =
        new User(
            UUID.randomUUID(),
            tenantId,
            email,
            passwordHash,
            request.adminFullName().trim(),
            Role.ORG_ADMIN,
            true,
            false,
            now);
    userRepository.save(admin);

    TokenResponse tokens = issueTokens(admin);
    return RegisterTenantResponse.of(
        TenantResponse.from(tenant), UserResponse.from(admin), tokens);
  }

  @Transactional(readOnly = true)
  public TokenResponse login(LoginRequest request) {
    String slug = request.tenantSlug().toLowerCase(Locale.ROOT).trim();
    Tenant tenant =
        tenantRepository
            .findBySlug(slug)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid credentials"));

    String email = normalizeEmail(request.email());
    User user =
        userRepository
            .findByTenantIdAndEmailIgnoreCase(tenant.getId(), email)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid credentials"));

    if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ApiException(
          ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    return issueTokens(user);
  }

  @Transactional(readOnly = true)
  public MeResponse me(AuthenticatedUser principal) {
    User user =
        userRepository
            .findById(principal.userId())
            .orElseThrow(
                () ->
                    new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Unauthorized"));
    Tenant tenant =
        tenantRepository
            .findById(principal.tenantId())
            .orElseThrow(
                () ->
                    new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Unauthorized"));

    if (!user.getTenantId().equals(tenant.getId())) {
      throw new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    return new MeResponse(UserResponse.from(user), TenantResponse.from(tenant));
  }

  @Transactional
  public UserResponse changePassword(
      AuthenticatedUser principal, ChangePasswordRequest request) {
    User user =
        userRepository
            .findById(principal.userId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Unauthorized"));

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ApiException(
          ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setMustChangePassword(false);
    return UserResponse.from(user);
  }

  private TokenResponse issueTokens(User user) {
    String accessToken = jwtService.createAccessToken(user);
    long expiresIn = jwtService.accessTokenExpiresInSeconds();
    // refreshToken is a placeholder until MUH-9 wires rotation.
    return new TokenResponse(accessToken, null, expiresIn);
  }

  static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
