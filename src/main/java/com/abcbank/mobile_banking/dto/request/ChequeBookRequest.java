package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChequeBookRequest(

        @NotBlank
        @Size(max = 30)
        String accountNo,

        @NotNull(message = "noofLeaves is required")
        Integer noofLeaves,

        @NotBlank
        @Pattern(
                regexp = "(?i)BRANCH|PRIMARY_ADDRESS|DEFAULT_ADDRESS",
                message = "Delivery must be BRANCH, PRIMARY_ADDRESS or DEFAULT_ADDRESS"
        )
        String delivery

) {}