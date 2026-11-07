
package com.abcbank.mobile_banking.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record StopPaymentRequest(

    @NotBlank
    @Size(max = 30)
    String accountNo,

    @NotBlank
    @Size(max = 30)
    String chequeNo,

    @PastOrPresent
    LocalDate chequeDate,

    @Size(max = 150)
    String issuedTo,

    @DecimalMin(value = "0.01")
    @Digits(integer = 16, fraction = 2)
    BigDecimal chequeAmount,

    @Size(max = 255)
    String reason

) {}

