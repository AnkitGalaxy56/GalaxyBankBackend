package com.abcbank.mobile_banking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferStatusResponse(
        Long requestId,
        String status,
        BigDecimal amount,
        String mode,
        String utr,
        Instant createdAt
) {
}