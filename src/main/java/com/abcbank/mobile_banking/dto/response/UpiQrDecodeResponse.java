
package com.abcbank.mobile_banking.dto.response;

import java.math.BigDecimal;

public record UpiQrDecodeResponse(
    String payeeName,
    String vpa,
    BigDecimal amount
) {}
