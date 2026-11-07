package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyLoginOtpRequest(

    @NotBlank(message = "Verification ID is required")
    String verificationId,

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "\\d{4}", message = "OTP must contain 4 digits")
    String otp

) {}