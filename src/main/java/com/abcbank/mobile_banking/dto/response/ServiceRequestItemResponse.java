package com.abcbank.mobile_banking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ServiceRequestItemResponse(
        String requestId,
        String type,
        String status,
        String summary,
        BigDecimal amount,
        Instant createdOn
) {
}