package com.abcbank.mobile_banking.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DepositRepository {
    private final JdbcTemplate jdbc;
    public DepositRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }


    

    public Long customerUserId(String customerNo) {
        return jdbc.queryForObject("""
            SELECT user_id FROM public."MB001001"
            WHERE customer_no = ? AND user_role = 'CUSTOMER' AND status = 'ACTIVE'
            """, Long.class, customerNo);
    }

    public List<Map<String,Object>> schemes(String type) {
        return jdbc.queryForList("""
            SELECT scheme_code, scheme_name, prod_code, prod_name, deposit_type,
                   min_deposit, max_deposit, min_tenure_months, max_tenure_months,
                   interest_rate, compounding_frequency, interest_payout_frequency
            FROM public."MB002004"
            WHERE deposit_type = ? AND is_active = TRUE
              AND effective_from <= CURRENT_DATE
              AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
            ORDER BY scheme_name
            """, type);
    }

    public Optional<Map<String,Object>> scheme(String code, String type) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT scheme_code, scheme_name, prod_code, prod_name, deposit_type,
                   min_deposit, max_deposit, min_tenure_months, max_tenure_months,
                   interest_rate, compounding_frequency
            FROM public."MB002004"
            WHERE scheme_code = ? AND deposit_type = ? AND is_active = TRUE
              AND effective_from <= CURRENT_DATE
              AND (effective_to IS NULL OR effective_to >= CURRENT_DATE)
            """, code, type);
        return rows.stream().findFirst();
    }

    public Optional<Map<String,Object>> account(String accountNo, String customerNo) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT account_no, customer_no, branch_code, account_type, account_status
            FROM public."MB002003" WHERE account_no = ? AND customer_no = ?
            """, accountNo, customerNo);
        return rows.stream().findFirst();
    }

    public Long insertOpening(String table, String branch, String customer, String debit,
            String debitStatus, String debitType, String prodCode, String prodName,
            String schemeCode, String schemeName, Date depositDate, BigDecimal amount,
            BigDecimal rate, int tenure, boolean autoRenew, String interestAccount,
            String maturityAccount, boolean fd, Long userId, Date maturityDate,
            BigDecimal maturityValue) {
        String extraColumn = fd ? ", renew_int" : "";
        String extraValue = fd ? ", ?" : "";
        String sql = """
            INSERT INTO public.%s
            (branch_code, customer_no, account_no, debit_account_status, debit_account_type,
             req_status, prod_code, prod_name, scheme_code, scheme_name, asof_date,
             deposit_amount, int_rate, tenure, auto_renewal, maturity_date, maturity_value,
             cr_int_acid, maturity_acid%s, created_by)
            VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?%s, ?)
            RETURNING id
            """.formatted(table, extraColumn, extraValue);
        if (fd) {
            return jdbc.queryForObject(sql, Long.class, branch, customer, debit, debitStatus,
                    debitType, prodCode, prodName, schemeCode, schemeName, depositDate,
                    amount, rate, tenure, autoRenew, maturityDate, maturityValue,
                    interestAccount, maturityAccount, autoRenew, userId);
        }
        return jdbc.queryForObject(sql, Long.class, branch, customer, debit, debitStatus,
                debitType, prodCode, prodName, schemeCode, schemeName, depositDate,
                amount, rate, tenure, autoRenew, maturityDate, maturityValue,
                interestAccount, maturityAccount, userId);
    }

    public Long insertClosure(String branch, String customer, String settlementAccount,
            String accountStatus, String accountType, String depositAccount,
            String depositType, String mode, String beneficiaryName, String beneficiaryAccount,
            String beneficiaryBank, String beneficiaryIfsc, String remarks, Long userId) {
        return jdbc.queryForObject("""
            INSERT INTO public."MB004007"
            (branch_code, customer_no, account_no, account_status, account_type,
             req_status, fd_acid, deposit_type, proceeds_account_no,
             ben_name, ben_account, ben_bank, ben_ifsc, remarks, created_by)
            VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """, Long.class, branch, customer, settlementAccount, accountStatus,
                accountType, depositAccount, depositType,
                "SAME_BANK".equals(mode) ? settlementAccount : null,
                beneficiaryName, beneficiaryAccount, beneficiaryBank, beneficiaryIfsc,
                remarks, userId);
    }

    public Map<String,Object> accountByTypeFallback(String customerNo) {
        return jdbc.queryForMap("""
            SELECT account_no, branch_code, account_type, account_status
            FROM public."MB002003"
            WHERE customer_no = ? AND UPPER(account_type) = 'SAVINGS'
              AND UPPER(account_status) = 'ACTIVE'
            ORDER BY account_no LIMIT 1
            """, customerNo);
    }

    public Optional<Map<String,Object>> depositAccount(String accountNo, String customerNo) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT account_no, customer_no, branch_code, account_type, account_status
            FROM public."MB002003"
            WHERE account_no = ? AND customer_no = ? AND UPPER(account_type) IN ('FD','RD')
            """, accountNo, customerNo);
        return rows.stream().findFirst();
    }

    
public Optional<Map<String, Object>> depositAccount(
        Long depositId, String customerNo, String depositType) {

    // Only allow the two known deposit tables.
    String table;

    if ("FD".equalsIgnoreCase(depositType)) {
        table = "\"MB004004\"";
    } else if ("RD".equalsIgnoreCase(depositType)) {
        table = "\"MB004005\"";
    } else {
        throw new IllegalArgumentException("Invalid deposit type");
    }

    String sql = """
        SELECT
            id,
            id::text AS deposit_id,
            branch_code,
            customer_no,
            account_no,
            debit_account_status AS account_status,
            debit_account_type AS account_type,
            req_status,
            '%s' AS deposit_type
        FROM public.%s
        WHERE id = ?
          AND customer_no = ?
          AND UPPER(req_status) = 'ACTIVE'
        """.formatted(
            depositType.toUpperCase(),
            table
        );

    List<Map<String, Object>> rows =
            jdbc.queryForList(sql, depositId, customerNo);

    return rows.stream().findFirst();
}

}
