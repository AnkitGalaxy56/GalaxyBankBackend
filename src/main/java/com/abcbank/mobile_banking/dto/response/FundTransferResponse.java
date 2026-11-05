package com.abcbank.mobile_banking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record FundTransferResponse(Long requestId,String status,String paymentMode,BigDecimal amount,Instant submittedAt,String message) {}
