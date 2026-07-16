package com.carebridge.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "carebridge")
public class CarebridgeProperties {

  private final Jwt jwt = new Jwt();
  private final Auth auth = new Auth();
  private final Cors cors = new Cors();

  public Jwt getJwt() {
    return jwt;
  }

  public Auth getAuth() {
    return auth;
  }

  public Cors getCors() {
    return cors;
  }

  public static class Jwt {
    private String secret = "carebridge-local-dev-secret-change-me-32b";
    private String issuer = "carebridge-local";
    private Duration accessTokenTtl = Duration.ofMinutes(15);

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
}
