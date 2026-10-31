package com.abcbank.mobile_banking.dto.response;

public record RegistrationTokenResponse(String message, String accessToken, String tokenType, long expiresInSeconds, String customerId, String userType) {}
