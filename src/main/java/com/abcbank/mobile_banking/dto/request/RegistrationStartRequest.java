package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegistrationStartRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{3,30}$", message = "Invalid customer ID format") String customerId,
    @NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit Indian mobile number") String mobileNumber
) {}
