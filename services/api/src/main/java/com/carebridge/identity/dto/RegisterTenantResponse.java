package com.carebridge.identity.dto;

public record RegisterTenantResponse(
    TenantResponse tenant, UserResponse user, TokensWrapper tokens, String webhookSecret) {

  public record TokensWrapper(String accessToken, String refreshToken, long expiresIn) {

    public static TokensWrapper from(TokenResponse tokens) {
      return new TokensWrapper(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
  }

  public static RegisterTenantResponse of(
      TenantResponse tenant, UserResponse user, TokenResponse tokens, String webhookSecret) {
    return new RegisterTenantResponse(tenant, user, TokensWrapper.from(tokens), webhookSecret);
  }
}
