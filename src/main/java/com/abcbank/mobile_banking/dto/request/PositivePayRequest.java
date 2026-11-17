
package com.abcbank.mobile_banking.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PositivePayRequest(

    @NotBlank(message = "Account number is required")
    @Size(max = 30)
    String accountNo,

    @NotBlank(message = "Cheque number is required")
    @Pattern(
        regexp = "^[0-9]{6}$",
        message = "Cheque number must contain exactly 6 digits"
    )
    String chequeNo,

    @NotNull(message = "Cheque date is required")
    LocalDate chequeDate,

    @NotNull(message = "Cheque amount is required")
    @DecimalMin(
        value = "50000.00",
        message = "Positive Pay is applicable for cheques of Rs. 50,000 and above"
    )
    @Digits(integer = 16, fraction = 2)
    BigDecimal chequeAmount,

    @NotBlank(message = "Payee name is required")
    @Size(max = 150)
    String payeeName,

    @Size(max = 255)
    String remarks,

    @NotNull(message = "Please confirm cheque details")
    Boolean confirmedDetails

) {}
