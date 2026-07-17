package com.carebridge.identity;

import com.carebridge.common.error.ApiException;
import com.carebridge.common.error.ErrorCode;
import com.carebridge.config.CarebridgeProperties;
import com.carebridge.identity.dto.ChangePasswordRequest;
import com.carebridge.identity.dto.LoginRequest;
import com.carebridge.identity.dto.MeResponse;
import com.carebridge.identity.dto.RefreshRequest;
import com.carebridge.identity.dto.RegisterTenantRequest;
import com.carebridge.identity.dto.RegisterTenantResponse;
import com.carebridge.identity.dto.TenantResponse;
import com.carebridge.identity.dto.TokenResponse;
import com.carebridge.identity.dto.UserResponse;
import com.carebridge.security.AuthenticatedUser;
import com.carebridge.security.JwtService;
import com.carebridge.webhooks.WebhookSecretService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenFamilyService refreshTokenFamilyService;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final CarebridgeProperties properties;
  private final WebhookSecretService webhookSecretService;

  public AuthService(
      TenantRepository tenantRepository,
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      RefreshTokenFamilyService refreshTokenFamilyService,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      CarebridgeProperties properties,
      WebhookSecretService webhookSecretService) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.refreshTokenFamilyService = refreshTokenFamilyService;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.properties = properties;
    this.webhookSecretService = webhookSecretService;
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
    WebhookSecretService.GeneratedSecret webhookSecret = webhookSecretService.generateEncrypted();
    Tenant tenant =
        new Tenant(
            tenantId,
            request.tenantName().trim(),
            slug,
            now,
            webhookSecret.ciphertext());
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

    TokenResponse tokens = issueTokens(admin, UUID.randomUUID());
    return RegisterTenantResponse.of(
        TenantResponse.from(tenant),
        UserResponse.from(admin),
        tokens,
        webhookSecret.plaintext());
  }

  @Transactional
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

    return issueTokens(user, UUID.randomUUID());
  }

  @Transactional
  public TokenResponse refresh(RefreshRequest request) {
    String presented = request.refreshToken().trim();
    String hash = hashToken(presented);
    Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(hash);

    if (found.isEmpty()) {
      throw unauthorizedRefresh();
    }

    RefreshToken stored = found.get();
    if (stored.isRevoked()) {
      // Commits in a nested TX so the throw below cannot undo family revoke.
      refreshTokenFamilyService.revokeFamily(stored.getFamilyId());
      throw unauthorizedRefresh();
    }

    if (stored.getExpiresAt().isBefore(Instant.now())) {
      stored.setRevoked(true);
      throw unauthorizedRefresh();
    }

    User user =
        userRepository
            .findById(stored.getUserId())
            .orElseThrow(AuthService::unauthorizedRefresh);

    if (!user.isActive()) {
      refreshTokenFamilyService.revokeFamily(stored.getFamilyId());
      throw unauthorizedRefresh();
    }

    stored.setRevoked(true);
    return issueTokens(user, stored.getFamilyId());
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

  private TokenResponse issueTokens(User user, UUID familyId) {
    String accessToken = jwtService.createAccessToken(user);
    long expiresIn = jwtService.accessTokenExpiresInSeconds();

    String rawRefresh = generateRawToken();
    Instant now = Instant.now();
    Instant refreshExpires = now.plus(properties.getJwt().getRefreshTokenTtl());

    RefreshToken row =
        new RefreshToken(
            UUID.randomUUID(),
            user.getId(),
            hashToken(rawRefresh),
            familyId,
            refreshExpires,
            false,
            now);
    refreshTokenRepository.save(row);

    return new TokenResponse(accessToken, rawRefresh, expiresIn);
  }

  private static ApiException unauthorizedRefresh() {
    return new ApiException(
        ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid refresh token");
  }

  private static String generateRawToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
