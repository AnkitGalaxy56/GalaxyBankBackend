package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebLoginRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 80)
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 128)
        String password
) {}
