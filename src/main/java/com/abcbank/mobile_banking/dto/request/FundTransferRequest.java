package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record FundTransferRequest(
    @NotBlank @Size(max=30) String debitAccountId,
    @NotBlank @Pattern(regexp="(?i)RTGS|NEFT|IMPS") String paymentMode,
    @NotBlank @Size(max=150) String beneficiaryName,
    @NotBlank @Size(max=34) String accountNo,
    @NotBlank @Size(max=34) String reConfirmAccountNo,
    @NotBlank @Pattern(regexp="(?i)[A-Z]{4}0[A-Z0-9]{6}") String ifscCode,
    @NotBlank @Pattern(regexp="[6-9][0-9]{9}") String mobileNo,
    @NotNull @DecimalMin(value="0.01") @Digits(integer=16,fraction=2) BigDecimal amount,
    @Size(max=255) String remarks,
    @AssertTrue(message="Customer authorization is required") boolean authorized
) {}
