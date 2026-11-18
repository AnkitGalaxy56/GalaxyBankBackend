package com.abcbank.mobile_banking.dto.response;

public record MobileAuthResponse(
        String message,
        String accessToken,
        String refreshToken,
        String tokenType,
        String customerId,
        long expiresInSec
) {}
