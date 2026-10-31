package com.abcbank.mobile_banking.dto.response;

public record RegistrationStartResponse(String message, String maskedMobile, int expiresInSeconds) {}
