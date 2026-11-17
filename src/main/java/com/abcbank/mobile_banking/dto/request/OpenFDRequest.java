package com.abcbank.mobile_banking.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OpenFDRequest(
        @NotBlank String schemeCode,
        @NotNull @DecimalMin("0.01") BigDecimal principalAmount,
        @NotNull @Positive Integer tenureMonths,
        @NotNull LocalDate depositDate,
        @NotBlank String debitAccountId,
        @NotBlank String interestCreditAccountId,
        String maturityAccountId,
        boolean autoRenewal) { }
