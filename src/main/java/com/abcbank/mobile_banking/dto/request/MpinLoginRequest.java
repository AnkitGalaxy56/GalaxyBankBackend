package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MpinLoginRequest(
        @NotBlank(message = "Customer ID is required") String customerId,
        @NotBlank(message = "MPIN is required")
        @Pattern(regexp = "^[0-9]{4}$", message = "MPIN must contain exactly 4 digits") String mpin,
        @NotBlank(message = "Device ID is required")
        @Pattern(regexp = "^[A-Za-z0-9._:-]{8,128}$", message = "Device ID must be 8-128 valid characters") String deviceId
) {}
