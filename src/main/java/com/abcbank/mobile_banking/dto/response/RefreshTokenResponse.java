package com.abcbank.mobile_banking.dto.response;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSec
) {}
