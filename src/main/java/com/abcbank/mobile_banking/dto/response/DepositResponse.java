package com.abcbank.mobile_banking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DepositResponse(
        String message,
        Long requestId,
        String depositType,
        String schemeCode,
        BigDecimal depositAmount,
        BigDecimal interestRate,
        Integer tenureMonths,
        LocalDate maturityDate,
        BigDecimal maturityAmount,
        String status) { }
