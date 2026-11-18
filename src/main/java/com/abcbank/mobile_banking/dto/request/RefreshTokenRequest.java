package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required") String refreshToken,
        @NotBlank(message = "Device ID is required")
        @Pattern(regexp = "^[A-Za-z0-9._:-]{8,128}$", message = "Device ID must be 8-128 valid characters") String deviceId
) {}
