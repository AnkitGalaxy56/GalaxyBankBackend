
package com.abcbank.mobile_banking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PositivePayResponse(

    Long id,

    String customerNo,

    String accountNo,

    String chequeNo,

    LocalDate chequeDate,

    BigDecimal chequeAmount,

    String payeeName,

    String remarks,

    String status,

    OffsetDateTime createdOn,

    String message

) {}
