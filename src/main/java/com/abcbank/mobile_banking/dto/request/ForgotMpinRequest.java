package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ForgotMpinRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{3,30}$", message = "Invalid customer ID format")
        String customerId
) {}
