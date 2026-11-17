package com.abcbank.mobile_banking.service;

import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abcbank.mobile_banking.dto.response.CustomerMeResponse;
import com.abcbank.mobile_banking.dto.response.CustomerMeResponse.AccountResponse;
import java.math.BigDecimal;

@Service
public class CustomerMeService {

    private final JdbcTemplate jdbc;

    public CustomerMeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CustomerMeResponse getMyDetails(String customerId) {

        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid customer authentication."
            );
        }

        Map<String, Object> customer;

        try {
            customer = jdbc.queryForMap("""
                SELECT
                    u.customer_no,
                    u.title,
                    u.first_name,
                    u.middle_name,
                    u.last_name,
                    u.full_name,
                    u.email,
                    u.user_type,
                    u.status AS user_status,
                    u.branch,
                    u.login_time,
                    cr.registered_mobile AS mobile
                FROM public."MB001001" u
                JOIN public.mb001001cr cr
                    ON cr.customer_id = u.customer_no
                WHERE u.customer_no = ?
                  AND u.user_type = 'CU'
                  AND u.status = 'ACTIVE'
                """, customerId);

        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException(
                    "Active customer not found."
            );
        }

        List<AccountResponse> accounts = jdbc.query("""
            SELECT
                a.account_no,
                a.account_type,
                a.account_status,
                a.branch_code,
                b.branch_name
            FROM public."MB002003" a
            LEFT JOIN public."MB002002" b
                ON b.branch_code = a.branch_code
            WHERE a.customer_no = ?
            ORDER BY a.created_on DESC
            """,
                (rs, rowNum) -> new AccountResponse(
                        rs.getString("account_no"),
                        rs.getString("account_type"),
                        rs.getString("account_status"),
                        rs.getString("branch_code"),
                        rs.getString("branch_name"),
                        new BigDecimal("25000000.00")
                ),
                customerId
        );

        Object loginTime = customer.get("login_time");

        return new CustomerMeResponse(
                getString(customer, "customer_no"),
                getString(customer, "title"),
                getString(customer, "first_name"),
                getString(customer, "middle_name"),
                getString(customer, "last_name"),
                getString(customer, "full_name"),
                getString(customer, "email"),
                getString(customer, "mobile"),
                getString(customer, "user_type"),
                getString(customer, "user_status"),
                getString(customer, "branch"),
                loginTime == null ? null : loginTime.toString(),
                accounts
        );
    }

    private String getString(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }
}