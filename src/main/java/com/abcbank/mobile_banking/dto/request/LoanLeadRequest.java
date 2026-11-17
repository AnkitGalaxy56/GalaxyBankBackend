
package com.abcbank.mobile_banking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoanLeadRequest(

    @NotBlank
    @Size(max = 20)
    String productType,

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    BigDecimal amount,

    @NotNull
    @Min(1)
    @Max(360)
    Integer tenureMonths,

    @NotBlank
    @Size(max = 500)
    String purpose,

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 16, fraction = 2)
    BigDecimal totalIncome,

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 16, fraction = 2)
    BigDecimal householdExpense,

    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    BigDecimal securityValue

) {}
