package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyRegistrationOtpRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{3,30}$") String customerId,
    @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "OTP must contain 6 digits") String otp
) {}
