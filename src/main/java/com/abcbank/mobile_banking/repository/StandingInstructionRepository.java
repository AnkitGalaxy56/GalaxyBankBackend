
package com.abcbank.mobile_banking.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StandingInstructionRepository {

    private final JdbcTemplate jdbc;

    public StandingInstructionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Find active customer user
    public Long findActiveCustomerUserId(String customerNo) {
        return jdbc.queryForObject("""
                SELECT user_id
                FROM public."MB001001"
                WHERE customer_no = ?
                  AND user_role = 'CUSTOMER'
                  AND status = 'ACTIVE'
                """, Long.class, customerNo);
    }

    // Debit account dropdown
    public List<Map<String, Object>> findDebitAccounts(String customerNo) {
        return jdbc.queryForList("""
                SELECT account_no, account_type,
                       account_status, branch_code
                FROM public."MB002003"
                WHERE customer_no = ?
                  AND UPPER(account_status) = 'ACTIVE'
                  AND UPPER(account_type) IN ('SAVINGS', 'CURRENT')
                ORDER BY account_no
                """, customerNo);
    }

    // Validate debit account ownership
    public Optional<Map<String, Object>> findDebitAccount(
            String accountNo, String customerNo) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT account_no, customer_no, branch_code,
                       account_type, account_status
                FROM public."MB002003"
                WHERE account_no = ?
                  AND customer_no = ?
                LIMIT 1
                """, accountNo, customerNo);

        return rows.stream().findFirst();
    }

    // Validate credit account
    public Optional<Map<String, Object>> findCreditAccount(String accountNo) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT account_no, account_type, account_status
                FROM public."MB002003"
                WHERE account_no = ?
                LIMIT 1
                """, accountNo);

        return rows.stream().findFirst();
    }

    // Insert standing instruction
    public Long insertStandingInstruction(
            String branchCode,
            String customerNo,
            String debitAccountNo,
            String debitAccountStatus,
            String debitAccountType,
            BigDecimal amount,
            Integer tenure,
            Date startDate,
            Integer debitDay,
            Date expiryDate,
            String creditAccountNo,
            String creditAccountStatus,
            Long createdBy) {

        return jdbc.queryForObject("""
                INSERT INTO public."MB004008" (
                    branch_code,
                    customer_no,
                    account_no,
                    account_status,
                    account_type,
                    debit_amount,
                    tenure,
                    frequency,
                    expiry_date,
                    cr_acid,
                    cr_ac_status,
                    created_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'MONTHLY', ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                branchCode,
                customerNo,
                debitAccountNo,
                debitAccountStatus,
                debitAccountType,
                amount,
                tenure,
                expiryDate,
                creditAccountNo,
                creditAccountStatus,
                createdBy);
    }

    // Get all customer instructions
    public List<Map<String, Object>> findCustomerInstructions(String customerNo) {

        return jdbc.queryForList("""
                SELECT
                    id,
                    branch_code,
                    customer_no,
                    account_no,
                    account_status,
                    account_type,
                    debit_amount,
                    tenure,
                    frequency,
                    expiry_date,
                    cr_acid,
                    cr_ac_status,
                    cr_ac_name,
                    req_status,
                    created_on
                FROM public."MB004008"
                WHERE customer_no = ?
                ORDER BY created_on DESC
                """, customerNo);
    }

    // Get instruction by ID
    public Optional<Map<String, Object>> findCustomerInstruction(
            Long id, String customerNo) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT
                    id,
                    account_no,
                    debit_amount,
                    tenure,
                    frequency,
                    expiry_date,
                    cr_acid,
                    cr_ac_status,
                    req_status,
                    created_on
                FROM public."MB004008"
                WHERE id = ?
                  AND customer_no = ?
                """, id, customerNo);

        return rows.stream().findFirst();
    }

    // Cancel pending instruction
    public int cancelPendingInstruction(
            Long id, String customerNo, Long userId) {

        return jdbc.update("""
                UPDATE public."MB004008"
                SET req_status = 'CANCELLED',
                    modified_by = ?,
                    modified_on = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND customer_no = ?
                  AND req_status = 'PENDING'
                """, userId, id, customerNo);
    }
}
