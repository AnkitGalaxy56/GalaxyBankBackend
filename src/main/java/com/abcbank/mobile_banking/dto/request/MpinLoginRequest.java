package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MpinLoginRequest(

        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotBlank(message = "MPIN is required")
        @Pattern(
            regexp = "^[0-9]{4}$",
            message = "MPIN must contain exactly 4 digits"
        )
        String mpin

) {}
