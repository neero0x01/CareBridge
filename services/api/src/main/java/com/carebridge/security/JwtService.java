package com.carebridge.security;

import com.carebridge.config.CarebridgeProperties;
import com.carebridge.identity.Role;
import com.carebridge.identity.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  public static final String CLAIM_TENANT_ID = "tenant_id";
  public static final String CLAIM_ROLE = "role";
  public static final String CLAIM_EMAIL = "email";

  private final CarebridgeProperties properties;
  private final byte[] secretBytes;

  public JwtService(CarebridgeProperties properties) {
    this.properties = properties;
    this.secretBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < 32) {
      throw new IllegalStateException("carebridge.jwt.secret must be at least 32 bytes for HS256");
    }
  }

  public String createAccessToken(User user) {
    Instant now = Instant.now();
    Instant exp = now.plus(properties.getJwt().getAccessTokenTtl());

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(user.getId().toString())
            .jwtID(UUID.randomUUID().toString())
            .issuer(properties.getJwt().getIssuer())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(exp))
            .claim(CLAIM_TENANT_ID, user.getTenantId().toString())
            .claim(CLAIM_ROLE, user.getRole().name())
            .claim(CLAIM_EMAIL, user.getEmail())
            .build();

    try {
      SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      signed.sign(new MACSigner(secretBytes));
      return signed.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign access token", e);
    }
  }

  public long accessTokenExpiresInSeconds() {
    return properties.getJwt().getAccessTokenTtl().toSeconds();
  }

  public AuthenticatedUser parseAndValidate(String token) {
    try {
      SignedJWT signed = SignedJWT.parse(token);
      if (!signed.verify(new MACVerifier(secretBytes))) {
        throw new InvalidTokenException("Invalid token signature");
      }

      JWTClaimsSet claims = signed.getJWTClaimsSet();
      Date expiration = claims.getExpirationTime();
      if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
        throw new InvalidTokenException("Token expired");
      }

      String issuer = claims.getIssuer();
      if (issuer == null || !issuer.equals(properties.getJwt().getIssuer())) {
        throw new InvalidTokenException("Invalid token issuer");
      }

      UUID userId = UUID.fromString(claims.getSubject());
      UUID tenantId = UUID.fromString(claims.getStringClaim(CLAIM_TENANT_ID));
      Role role = Role.valueOf(claims.getStringClaim(CLAIM_ROLE));
      String email = claims.getStringClaim(CLAIM_EMAIL);

      return new AuthenticatedUser(userId, tenantId, role, email);
    } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
      throw new InvalidTokenException("Invalid token", e);
    }
  }

  public static class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
      super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
