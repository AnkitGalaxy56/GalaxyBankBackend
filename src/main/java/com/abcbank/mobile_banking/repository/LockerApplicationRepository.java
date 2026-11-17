
package com.abcbank.mobile_banking.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LockerApplicationRepository {

    private final JdbcTemplate jdbc;

    public LockerApplicationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Supports JWT subject containing either user_id or customer_no.
    public Map<String, Object> customerByPrincipal(String principal) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT user_id, customer_no
            FROM public."MB001001"
            WHERE status = 'ACTIVE'
              AND user_role = 'CUSTOMER'
              AND (
                    customer_no = ?
                    OR user_id::text = ?
              )
            """, principal, principal);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Active customer was not found for this login");
        }

        return rows.get(0);
    }

    public Map<String, Object> activeSavingsAccount(
            String accountNo, String customerNo) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT account_no, customer_no, branch_code,
                   account_type, account_status
            FROM public."MB002003"
            WHERE account_no = ?
              AND customer_no = ?
              AND UPPER(account_type) = 'SAVINGS'
              AND UPPER(account_status) = 'ACTIVE'
            """, accountNo, customerNo);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Active Savings account was not found for this customer");
        }

        return rows.get(0);
    }

    public boolean hasPendingApplication(
            String customerNo, String accountNo) {

        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM public."MB004009"
            WHERE customer_no = ?
              AND account_no = ?
              AND UPPER(req_status::text) = 'PENDING'
            """, Integer.class, customerNo, accountNo);

        return count != null && count > 0;
    }

    public Long insertApplication(
            String branchCode,
            String customerNo,
            String accountNo,
            String accountStatus,
            String accountType,
            String mobile,
            String email,
            int operationYears,
            String lockerType,
            String operationMode,
            String nomineeName,
            Long userId) {

        return jdbc.queryForObject("""
            INSERT INTO public."MB004009"
            (
                branch_code,
                customer_no,
                account_no,
                account_status,
                account_type,
                req_status,
                mobile,
                email,
                operation_yrs,
                locker_type,
                operation_mode,
                nominee_name,
                created_by,
                created_on
            )
            VALUES (
                ?, ?, ?, ?, ?, 'PENDING',
                ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
            )
            RETURNING id
            """,
            Long.class,
            branchCode,
            customerNo,
            accountNo,
            accountStatus,
            accountType,
            mobile,
            email,
            operationYears,
            lockerType,
            operationMode,
            nomineeName,
            userId);
    }
}
