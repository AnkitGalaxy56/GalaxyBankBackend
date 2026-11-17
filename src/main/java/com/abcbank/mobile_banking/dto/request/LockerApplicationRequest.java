package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LockerApplicationRequest(

        @NotBlank(message = "Account number is required")
        String accountNo,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$",
                message = "Enter a valid 10-digit mobile number")
        String mobile,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @Min(value = 1, message = "Minimum operation period is 1 year")
        @Max(value = 25, message = "Maximum operation period is 25 years")
        int operationYears,

        @NotBlank(message = "Locker type is required")
        String lockerType,

        @NotBlank(message = "Operation mode is required")
        String operationMode,

        @NotBlank(message = "Nominee name is required")
        String nomineeName,

        @AssertTrue(message = "Terms and conditions must be accepted")
        boolean termsAccepted,

        @AssertTrue(message = "Declaration must be accepted")
        boolean declarationAccepted,

        @AssertTrue(message = "Privacy consent must be accepted")
        boolean privacyConsent

) {}