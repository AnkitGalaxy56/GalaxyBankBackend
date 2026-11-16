
package com.abcbank.mobile_banking.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CustomerMeResponse(
        String customerId,
        String title,
        String firstName,
        String middleName,
        String lastName,
        String fullName,
        String email,
        String mobile,
        String userType,
        String userStatus,
        String branch,
        String lastLogin,
        List<AccountResponse> accounts
) {
    public record AccountResponse(
            String accountNumber,
            String accountType,
            String accountStatus,
            String branchCode,
            String branchName,
            BigDecimal balance
    ) {
    }
}
