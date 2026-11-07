package com.abcbank.mobile_banking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StandingInstructionRequest(
        @NotBlank @Size(max = 30)
        String debitAccountId,

        @NotBlank @Size(max = 30)
        String creditAccountId,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 16, fraction = 2)
        BigDecimal amount,

        @NotNull @Min(1) @Max(120)
        Integer tenure,

        @NotNull @Min(1) @Max(31)
        Integer debitDay
) {
}
