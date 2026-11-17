package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CloseDepositRequest(
        @NotBlank String depositType,
        @NotBlank String depositAccountId,
        @NotBlank String proceedsMode, // SAME_BANK or OTHER_BANK
        String proceedsAccountId,
        String beneficiaryName,
        String beneficiaryAccount,
        String beneficiaryBank,
        String beneficiaryIfsc,
        String remarks) { }
