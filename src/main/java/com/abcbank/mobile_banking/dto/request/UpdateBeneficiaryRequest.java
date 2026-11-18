
package com.abcbank.mobile_banking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBeneficiaryRequest(

        @NotBlank(message = "Beneficiary name is required")
        @Size(max = 150, message = "Beneficiary name cannot exceed 150 characters")
        String beneficiaryName,

        @NotNull(message = "Single transaction limit is required")
        @DecimalMin(value = "0.01", message = "Limit must be greater than zero")
        @DecimalMax(value = "50000.00", message = "Maximum transaction limit is Rs. 50,000")
        BigDecimal singleTransactionLimit,

        @NotBlank(message = "MPIN is required")
        @Pattern(regexp = "^[0-9]{4}$", message = "MPIN must contain exactly 4 digits")
        String mpin

) {
}
