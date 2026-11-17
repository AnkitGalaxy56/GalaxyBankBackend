package com.abcbank.mobile_banking.service;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class CustomerIdentityResolver {

    private final JdbcTemplate jdbc;

    public CustomerIdentityResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CustomerIdentity resolve(String principal) {
        if (principal == null || principal.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Login is required");
        }

        var rows = jdbc.queryForList("""
            SELECT user_id, customer_no
              FROM public."MB001001"
             WHERE status = 'ACTIVE'
               AND user_type = 'CU'
               AND (customer_no = ? OR user_id::text = ?)
             LIMIT 1
            """, principal, principal);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Active customer was not found for this login");
        }

        Map<String, Object> row = rows.get(0);
        return new CustomerIdentity(
                ((Number) row.get("user_id")).longValue(),
                String.valueOf(row.get("customer_no")));
    }

    public record CustomerIdentity(Long userId, String customerNo) {}
}
