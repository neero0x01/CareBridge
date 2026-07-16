package com.carebridge.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TokenResponse(
    String accessToken,
    /** Placeholder until refresh rotation (MUH-9) is wired. */
    String refreshToken,
    long expiresIn) {}
