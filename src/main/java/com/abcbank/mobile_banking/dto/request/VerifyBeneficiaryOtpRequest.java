
package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerifyBeneficiaryOtpRequest(

        @NotNull(message = "Beneficiary ID is required")
        Long beneficiaryId,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be 6 digits")
        String otp

) {
}
