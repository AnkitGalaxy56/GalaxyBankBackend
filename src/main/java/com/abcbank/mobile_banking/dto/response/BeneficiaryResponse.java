

package com.abcbank.mobile_banking.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BeneficiaryResponse(

        Long id,
        String beneficiaryName,
        String beneficiaryAccount,
        String beneficiaryBank,
        String beneficiaryIfsc,
        BigDecimal singleTransactionLimit,
        String beneficiaryEmail,
        String requestStatus,
        boolean verified,
        boolean active,
        OffsetDateTime createdOn

) {
}
