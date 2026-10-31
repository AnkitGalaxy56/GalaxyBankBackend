package com.abcbank.mobile_banking.dto.response;
public record AuthResponse(
        String message,
        String accessToken,
        String tokenType,
        String customerId
) {}
