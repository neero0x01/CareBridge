package com.carebridge.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carebridge")
public class CarebridgeProperties {

  private final Jwt jwt = new Jwt();
  private final Auth auth = new Auth();
  private final Cors cors = new Cors();
  private final Webhooks webhooks = new Webhooks();

  public Jwt getJwt() {
    return jwt;
  }

  public Auth getAuth() {
    return auth;
  }

  public Cors getCors() {
    return cors;
  }

  public Webhooks getWebhooks() {
    return webhooks;
  }

  public static class Jwt {
    private String secret = "carebridge-local-dev-secret-change-me-32b";
    private String issuer = "carebridge-local";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }

    public String getIssuer() {
      return issuer;
    }

    public void setIssuer(String issuer) {
      this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
      return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
      this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
      return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
      this.refreshTokenTtl = refreshTokenTtl;
    }
  }

  public static class Auth {
    private boolean registerTenantEnabled = true;

    public boolean isRegisterTenantEnabled() {
      return registerTenantEnabled;
    }

    public void setRegisterTenantEnabled(boolean registerTenantEnabled) {
      this.registerTenantEnabled = registerTenantEnabled;
    }
  }

  public static class Cors {
    private String allowedOrigins = "http://localhost:3000";

    public String getAllowedOrigins() {
      return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
      this.allowedOrigins = allowedOrigins;
    }
  }

  /**
   * Webhook secret encryption. {@code encryptionKeyBase64} must decode to 32 bytes (AES-256). Local
   * default is for demos only — override via {@code WEBHOOK_ENCRYPTION_KEY} in real deploys.
   */
  public static class Webhooks {
    // Base64 of 32 zero-ish but fixed local-dev bytes (SHA-256 of a label), not production-safe.
    private String encryptionKeyBase64 =
        "Y2FyZWJyaWRnZS1sb2NhbC13ZWJob29rLWtleSEhISE="; // 32 ASCII bytes base64

    public String getEncryptionKeyBase64() {
      return encryptionKeyBase64;
    }

    public void setEncryptionKeyBase64(String encryptionKeyBase64) {
      this.encryptionKeyBase64 = encryptionKeyBase64;
    }
  }
}
