
package com.abcbank.mobile_banking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BeneficiaryRequest(

        @NotBlank(message = "Branch code is required")
        String branchCode,

        @NotBlank(message = "Beneficiary name is required")
        @Size(max = 150, message = "Beneficiary name cannot exceed 150 characters")
        String beneficiaryName,

        @NotBlank(message = "Beneficiary account number is required")
        @Size(max = 34, message = "Account number cannot exceed 34 characters")
        String beneficiaryAccount,

        @NotBlank(message = "Beneficiary bank name is required")
        @Size(max = 150, message = "Bank name cannot exceed 150 characters")
        String beneficiaryBank,

        @NotBlank(message = "IFSC code is required")
        @Pattern(
                regexp = "^[A-Z]{4}0[A-Z0-9]{6}$",
                message = "Invalid IFSC code"
        )
        String beneficiaryIfsc,

        @NotNull(message = "Single transaction limit is required")
        @DecimalMin(value = "0.01", message = "Transaction limit must be greater than zero")
        BigDecimal singleTransactionLimit,

        @Email(message = "Invalid beneficiary email")
        @Size(max = 254, message = "Email cannot exceed 254 characters")
        String beneficiaryEmail

) {
}
