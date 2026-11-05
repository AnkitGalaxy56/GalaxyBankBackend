package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyMpinResetOtpRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{3,30}$")
        String customerId,

        @NotBlank
        @Pattern(regexp = "^[0-9]{4}$", message = "OTP must contain 4 digits")
        String otp
) {}
