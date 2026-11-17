
package com.abcbank.mobile_banking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record StopPaymentResponse(

    Long id,
    String reqStatus,
    String accountNo,
    String chequeNo,
    LocalDate chequeDate,
    String issuedTo,
    BigDecimal chequeAmount,
    String reason,
    OffsetDateTime createdOn,
    String message

) {}
