package com.carebridge.identity.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
