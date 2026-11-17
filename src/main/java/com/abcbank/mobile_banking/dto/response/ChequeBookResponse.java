
package com.abcbank.mobile_banking.dto.response;

import java.time.OffsetDateTime;

public record ChequeBookResponse(

    Long id,
    String reqStatus,
    String accountNo,
    Integer noofBooks,
    String delivery,
    OffsetDateTime createdOn,
    String message

) {}
