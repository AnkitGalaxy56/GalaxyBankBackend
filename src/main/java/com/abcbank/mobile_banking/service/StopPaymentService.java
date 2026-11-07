
package com.abcbank.mobile_banking.service;

import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abcbank.mobile_banking.dto.request.StopPaymentRequest;
import com.abcbank.mobile_banking.dto.response.StopPaymentResponse;

@Service
public class StopPaymentService {

    private final JdbcTemplate jdbc;

    public StopPaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public StopPaymentResponse submit(
            String customerId,
            StopPaymentRequest request) {

        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException(
                "Invalid customer authentication."
            );
        }

        // 1. Validate logged-in customer
        Long createdBy;

        try {
            createdBy = jdbc.queryForObject("""
                SELECT user_id
                FROM public."MB001001"
                WHERE customer_no = ?
                  AND user_type = 'CU'
                  AND status = 'ACTIVE'
                """,
                Long.class,
                customerId
            );

        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException(
                "Active customer user record not found."
            );
        }

        // 2. Validate account ownership
        Map<String, Object> account;

        try {
            account = jdbc.queryForMap("""
                SELECT
                    account_no,
                    customer_no,
                    account_status,
                    account_type,
                    branch_code
                FROM public."MB002003"
                WHERE account_no = ?
                  AND customer_no = ?
                """,
                request.accountNo().trim(),
                customerId
            );

        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException(
                "Account not found or does not belong to you."
            );
        }

        // 3. Validate account status
        String accountStatus =
            String.valueOf(account.get("account_status"));

        if (!"ACTIVE".equalsIgnoreCase(accountStatus)) {
            throw new IllegalArgumentException(
                "Account is not active."
            );
        }

        // 4. Insert stop payment request
        return jdbc.queryForObject("""
            INSERT INTO public."MB004003" (
                branch_code,
                customer_no,
                account_no,
                account_status,
                account_type,
                cheque_no,
                cheque_date,
                issued_to,
                cheque_amount,
                reason,
                created_by
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING
                id,
                req_status,
                account_no,
                cheque_no,
                cheque_date,
                issued_to,
                cheque_amount,
                reason,
                created_on
            """,
            (rs, rowNum) -> new StopPaymentResponse(
                rs.getLong("id"),
                rs.getString("req_status"),
                rs.getString("account_no"),
                rs.getString("cheque_no"),
                rs.getObject(
                    "cheque_date",
                    java.time.LocalDate.class
                ),
                rs.getString("issued_to"),
                rs.getBigDecimal("cheque_amount"),
                rs.getString("reason"),
                rs.getObject(
                    "created_on",
                    java.time.OffsetDateTime.class
                ),
                "Stop payment request submitted successfully."
            ),
            account.get("branch_code"),
            customerId,
            account.get("account_no"),
            account.get("account_status"),
            account.get("account_type"),
            request.chequeNo().trim(),
            request.chequeDate(),
            request.issuedTo(),
            request.chequeAmount(),
            request.reason(),
            createdBy
        );
    }
}
