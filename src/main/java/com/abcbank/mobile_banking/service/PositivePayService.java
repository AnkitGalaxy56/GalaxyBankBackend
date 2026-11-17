
package com.abcbank.mobile_banking.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.request.PositivePayRequest;
import com.abcbank.mobile_banking.dto.response.PositivePayResponse;
import com.abcbank.mobile_banking.repository.PositivePayRepository;

@Service
public class PositivePayService {

    private final PositivePayRepository repository;
    private final JdbcTemplate jdbc;

    public PositivePayService(
            PositivePayRepository repository,
            JdbcTemplate jdbc) {

        this.repository = repository;
        this.jdbc = jdbc;
    }

    // 1. GET CUSTOMER ACCOUNTS
    public List<Map<String, Object>> getAccounts(
            String principal) {

        try {
            Map<String, Object> customer =
                repository.findCustomer(principal);

            String customerNo =
                String.valueOf(customer.get("customer_no"));

            return repository.findAccounts(customerNo);

        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, ex.getMessage()
            );
        }
    }

    // 2. VALIDATE CHEQUE DETAILS
    public Map<String, Object> validate(
            String principal,
            PositivePayRequest request) {

        try {
            Map<String, Object> customer =
                repository.findCustomer(principal);

            String customerNo =
                String.valueOf(customer.get("customer_no"));

            validateDetails(request);

            Map<String, Object> account =
                repository.findAccount(
                    request.accountNo().trim(),
                    customerNo
                );

            if (!"ACTIVE".equalsIgnoreCase(
                    String.valueOf(account.get("account_status")))) {

                throw new IllegalArgumentException(
                    "Selected account is not active"
                );
            }

            if (!repository.findAccounts(customerNo).stream()
                    .anyMatch(a -> request.accountNo().trim()
                    .equals(String.valueOf(a.get("account_no"))))) {

                throw new IllegalArgumentException(
                    "Only Savings and Current accounts are eligible"
                );
            }

            if (repository.chequeAlreadyRegistered(
                    request.accountNo().trim(),
                    request.chequeNo().trim())) {

                throw new IllegalArgumentException(
                    "This cheque is already registered"
                );
            }

            Map<String, Object> response =
                new LinkedHashMap<>();

            response.put("valid", true);
            response.put("accountNo", request.accountNo());
            response.put("chequeNo", request.chequeNo());
            response.put("message",
                "Cheque details validated successfully");

            return response;

        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, ex.getMessage()
            );
        }
    }

    // 3. SUBMIT POSITIVE PAY REQUEST
    @Transactional
    public PositivePayResponse submit(
            String principal,
            PositivePayRequest request) {

        try {
            Map<String, Object> customer =
                repository.findCustomer(principal);

            Long userId =
                ((Number) customer.get("user_id")).longValue();

            String customerNo =
                String.valueOf(customer.get("customer_no"));

            validateDetails(request);

            Map<String, Object> account =
                repository.findAccount(
                    request.accountNo().trim(),
                    customerNo
                );

            if (!"ACTIVE".equalsIgnoreCase(
                    String.valueOf(account.get("account_status")))) {

                throw new IllegalArgumentException(
                    "Account is not active"
                );
            }

            String accountType =
                String.valueOf(account.get("account_type"));

            if (!accountType.equalsIgnoreCase("SAVINGS")
                    && !accountType.equalsIgnoreCase("CURRENT")) {

                throw new IllegalArgumentException(
                    "Only Savings and Current accounts are supported"
                );
            }

            if (repository.chequeAlreadyRegistered(
                    request.accountNo().trim(),
                    request.chequeNo().trim())) {

                throw new IllegalArgumentException(
                    "This cheque is already registered"
                );
            }

            var inserted =
                repository.insertRequest(
                    account,
                    customerNo,
                    userId,
                    request.chequeNo().trim(),
                    request.chequeDate(),
                    request.chequeAmount(),
                    request.payeeName().trim(),
                    request.remarks()
                );

            return new PositivePayResponse(
                inserted.id(),
                customerNo,
                request.accountNo(),
                inserted.chequeNo(),
                inserted.chequeDate(),
                inserted.chequeAmount(),
                inserted.payeeName(),
                inserted.remarks(),
                inserted.status(),
                inserted.createdOn(),
                "Positive Pay request submitted successfully"
            );

        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, ex.getMessage()
            );
        }
    }

    // 4. GET REQUEST DETAILS
    public Map<String, Object> getRequest(
            String principal,
            Long id) {

        Map<String, Object> customer =
            getCustomer(principal);

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT *
            FROM public."MB004013"
            WHERE id = ?
              AND customer_no = ?
            """, id, customer.get("customer_no"));

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Positive Pay request not found"
            );
        }

        return rows.get(0);
    }

    // 5. CUSTOMER REQUEST HISTORY
    public List<Map<String, Object>> getHistory(
            String principal) {

        Map<String, Object> customer =
            getCustomer(principal);

        return jdbc.queryForList("""
            SELECT
                id,
                account_no,
                cheque_no,
                cheque_date,
                cheque_amount,
                payee_name,
                req_status,
                created_on
            FROM public."MB004013"
            WHERE customer_no = ?
            ORDER BY created_on DESC
            """, customer.get("customer_no"));
    }

    // 6. CANCEL PENDING REQUEST
    @Transactional
    public Map<String, Object> cancelRequest(
            String principal,
            Long id) {

        Map<String, Object> customer =
            getCustomer(principal);

        int updated = jdbc.update("""
            UPDATE public."MB004013"
            SET req_status = 'CANCELLED',
                modified_by = ?,
                modified_on = CURRENT_TIMESTAMP
            WHERE id = ?
              AND customer_no = ?
              AND req_status::text = 'PENDING'
            """,
            customer.get("user_id"),
            id,
            customer.get("customer_no")
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Request cannot be cancelled. It may not exist or is no longer pending."
            );
        }

        return Map.of(
            "id", id,
            "status", "CANCELLED",
            "message", "Positive Pay request cancelled successfully"
        );
    }

    // COMMON CUSTOMER VALIDATION
    private Map<String, Object> getCustomer(
            String principal) {

        try {
            return repository.findCustomer(principal);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, ex.getMessage()
            );
        }
    }

    // COMMON CHEQUE VALIDATION
    private void validateDetails(
            PositivePayRequest request) {

        LocalDate today = LocalDate.now();
        LocalDate minimumDate = today.minusMonths(3);

        if (request.chequeDate().isAfter(today)) {
            throw new IllegalArgumentException(
                "Cheque date cannot be in the future"
            );
        }

        if (request.chequeDate().isBefore(minimumDate)) {
            throw new IllegalArgumentException(
                "Cheque date cannot be older than 3 months"
            );
        }

        if (request.chequeAmount() == null
                || request.chequeAmount()
                .compareTo(new java.math.BigDecimal("50000.00")) < 0) {

            throw new IllegalArgumentException(
                "Cheque amount must be Rs. 50,000 or above"
            );
        }

        if (request.confirmedDetails() == null
                || !request.confirmedDetails()) {

            throw new IllegalArgumentException(
                "Please confirm that cheque details are correct"
            );
        }
    }
}
