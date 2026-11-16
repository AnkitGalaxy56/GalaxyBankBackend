
package com.abcbank.mobile_banking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmiPaymentRequest(

    @NotBlank
    @Size(max = 30)
    String debitAccountId,

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    BigDecimal amount

) {
}
