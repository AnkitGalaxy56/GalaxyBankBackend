
package com.abcbank.mobile_banking.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PositivePayRepository {

    private final JdbcTemplate jdbc;

    public PositivePayRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Find logged-in customer
    public Map<String, Object> findCustomer(String principal) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT user_id, customer_no
            FROM public."MB001001"
            WHERE status = 'ACTIVE'
              AND (
                    customer_no = ?
                    OR user_id::text = ?
              )
              AND (
                    UPPER(COALESCE(user_role, '')) IN ('CU', 'CUSTOMER')
                    OR UPPER(COALESCE(sys_role, '')) = 'CU'
              )
            """, principal, principal);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                "Active customer not found"
            );
        }

        return rows.get(0);
    }

    // Get customer's active savings/current accounts
    public List<Map<String, Object>> findAccounts(String customerNo) {

        return jdbc.queryForList("""
            SELECT
                account_no,
                customer_no,
                account_type,
                account_status,
                branch_code
            FROM public."MB002003"
            WHERE customer_no = ?
              AND UPPER(account_status) = 'ACTIVE'
              AND UPPER(account_type) IN ('SAVINGS', 'CURRENT')
            ORDER BY account_no
            """, customerNo);
    }

    // Verify account belongs to customer
    public Map<String, Object> findAccount(
            String accountNo,
            String customerNo) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
                account_no,
                customer_no,
                branch_code,
                account_type,
                account_status
            FROM public."MB002003"
            WHERE account_no = ?
              AND customer_no = ?
              AND UPPER(account_status) = 'ACTIVE'
            """, accountNo, customerNo);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                "Account not found or does not belong to you"
            );
        }

        return rows.get(0);
    }

    // Check duplicate cheque registration
    public boolean chequeAlreadyRegistered(
            String accountNo,
            String chequeNo) {

        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM public."MB004013"
            WHERE account_no = ?
              AND cheque_no = ?
              AND req_status IN (
                  'PENDING', 'APPROVED', 'COMPLETED'
              )
            """,
            Integer.class,
            accountNo,
            chequeNo
        );

        return count != null && count > 0;
    }

    // Insert Positive Pay request
    public InsertedRequest insertRequest(
            Map<String, Object> account,
            String customerNo,
            Long userId,
            String chequeNo,
            LocalDate chequeDate,
            BigDecimal chequeAmount,
            String payeeName,
            String remarks) {

        return jdbc.queryForObject("""
            INSERT INTO public."MB004013" (
                branch_code,
                customer_no,
                account_no,
                account_status,
                account_type,
                req_status,
                cheque_no,
                cheque_date,
                cheque_amount,
                payee_name,
                remarks,
                customer_confirmed,
                match_status,
                created_by,
                created_on
            )
            VALUES (
                ?, ?, ?, ?, ?,
                'PENDING',
                ?, ?, ?, ?, ?,
                TRUE,
                'NOT_PRESENTED',
                ?,
                CURRENT_TIMESTAMP
            )
            RETURNING
                id,
                cheque_no,
                cheque_date,
                cheque_amount,
                payee_name,
                remarks,
                req_status,
                created_on
            """,
            (rs, rowNum) -> new InsertedRequest(
                rs.getLong("id"),
                rs.getString("cheque_no"),
                rs.getObject("cheque_date", LocalDate.class),
                rs.getBigDecimal("cheque_amount"),
                rs.getString("payee_name"),
                rs.getString("remarks"),
                rs.getString("req_status"),
                rs.getObject("created_on", OffsetDateTime.class)
            ),
            account.get("branch_code"),
            customerNo,
            account.get("account_no"),
            account.get("account_status"),
            account.get("account_type"),
            chequeNo,
            chequeDate,
            chequeAmount,
            payeeName,
            remarks,
            userId
        );
    }

    public record InsertedRequest(
        Long id,
        String chequeNo,
        LocalDate chequeDate,
        BigDecimal chequeAmount,
        String payeeName,
        String remarks,
        String status,
        OffsetDateTime createdOn
    ) {}
}
