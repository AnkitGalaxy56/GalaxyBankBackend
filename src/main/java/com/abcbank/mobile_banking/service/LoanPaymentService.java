
package com.abcbank.mobile_banking.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.request.EmiPaymentRequest;
import com.abcbank.mobile_banking.dto.request.LoanLeadRequest;

@Service
public class LoanPaymentService {

    private static final ZoneId BANK_ZONE =
            ZoneId.of("Asia/Kolkata");

    private static final Pattern VPA_PATTERN =
            Pattern.compile(
                "[A-Za-z0-9._-]{2,256}@[A-Za-z0-9.-]{2,64}"
            );

    private final JdbcTemplate jdbc;

    public LoanPaymentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------
    // PAY-06: DECODE UPI QR
    // ------------------------------------------------

    public Map<String, Object> decodeUpiQr(String qrPayload) {

        try {
            URI uri = URI.create(qrPayload.trim());

            if (!"upi".equalsIgnoreCase(uri.getScheme())
                    || !"pay".equalsIgnoreCase(uri.getHost())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only UPI payment QR codes are accepted"
                );
            }

            if (uri.getRawQuery() == null) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid UPI QR payload"
                );
            }

            Map<String, String> params = new HashMap<>();

            for (String pair : uri.getRawQuery().split("&")) {

                String[] parts = pair.split("=", 2);

                String key = URLDecoder.decode(
                    parts[0], StandardCharsets.UTF_8
                );

                String value = parts.length > 1
                    ? URLDecoder.decode(
                        parts[1], StandardCharsets.UTF_8)
                    : "";

                params.put(key, value);
            }

            String vpa = params.get("pa");
            String payeeName = params.get("pn");

            if (vpa == null ||
                    !VPA_PATTERN.matcher(vpa).matches()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid UPI VPA"
                );
            }

            if (payeeName == null || payeeName.isBlank()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payee name is missing"
                );
            }

            BigDecimal amount = null;

            if (params.containsKey("am")
                    && !params.get("am").isBlank()) {

                try {
                    amount = new BigDecimal(params.get("am"));

                    if (amount.compareTo(BigDecimal.ZERO) <= 0
                            || amount.scale() > 2) {
                        throw new NumberFormatException();
                    }

                } catch (NumberFormatException ex) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid UPI QR amount"
                    );
                }
            }

            Map<String, Object> response = new HashMap<>();

            response.put("payeeName", payeeName.trim());
            response.put("vpa", vpa);
            response.put("amount", amount);

            return response;

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unable to decode UPI QR"
            );
        }
    }

    // ------------------------------------------------
    // LON-03: CREATE EMI PAYMENT REQUEST
    // ------------------------------------------------

    @Transactional
    public Map<String, Object> createEmiPayment(
            String customerNo,
            String loanId,
            EmiPaymentRequest request) {

        Long userId = findActiveCustomerUserId(customerNo);

        // Validate debit account ownership
        Map<String, Object> debit = findAccount(
            request.debitAccountId(), customerNo
        );

        String debitStatus =
            String.valueOf(debit.get("account_status"));

        String debitType =
            String.valueOf(debit.get("account_type"));

        if (!"ACTIVE".equalsIgnoreCase(debitStatus)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Debit account is not active"
            );
        }

        if (!"SAVINGS".equalsIgnoreCase(debitType)
                && !"CURRENT".equalsIgnoreCase(debitType)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Only Savings and Current accounts are allowed"
            );
        }

        // Validate loan account ownership
        Map<String, Object> loan = findAccount(
            loanId, customerNo
        );

        String loanStatus =
            String.valueOf(loan.get("account_status"));

        String loanType =
            String.valueOf(loan.get("account_type"));

        if (!"ACTIVE".equalsIgnoreCase(loanStatus)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Loan account is not active"
            );
        }

        if (!"LOAN".equalsIgnoreCase(loanType)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Selected account is not a loan account"
            );
        }

        // Daily cutoff: 5 PM Indian time
        LocalTime currentTime = LocalTime.now(BANK_ZONE);

        if (!currentTime.isBefore(LocalTime.of(17, 0))) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "EMI payment requests are not accepted after 5:00 PM IST"
            );
        }

        // IMPORTANT:
        // MB002003 does not contain available balance.
        // This endpoint only creates a pending request.
        // It does not debit the account.

        Long requestId = jdbc.queryForObject("""
            INSERT INTO public."MB004006" (
                branch_code,
                customer_no,
                account_no,
                account_status,
                account_type,
                loan_account_no,
                loan_ac_status,
                emi_amount,
                instal_amount,
                created_by
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """,
            Long.class,
            debit.get("branch_code"),
            customerNo,
            request.debitAccountId(),
            debitStatus,
            debitType,
            loanId,
            loanStatus,
            request.amount(),
            request.amount(),
            userId
        );

        Map<String, Object> response = new HashMap<>();

        response.put("requestId", requestId);
        response.put("status", "PENDING");
        response.put("loanAccountId", loanId);
        response.put("debitAccountId", request.debitAccountId());
        response.put("amount", request.amount());
        response.put("message",
            "EMI payment request submitted for processing");

        return response;
    }

    // ------------------------------------------------
    // LON-06: APPLY FOR LOAN
    // ------------------------------------------------

    @Transactional
    public Map<String, Object> createLoanLead(
            String customerNo,
            LoanLeadRequest request) {

        Long userId = findActiveCustomerUserId(customerNo);

        if (request.householdExpense()
                .compareTo(request.totalIncome()) > 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Household expense cannot exceed total income"
            );
        }

        // Get customer's active account and branch
        Map<String, Object> account = jdbc.queryForMap("""
            SELECT branch_code
            FROM public."MB002003"
            WHERE customer_no = ?
              AND account_status = 'ACTIVE'
            ORDER BY created_on
            LIMIT 1
            """, customerNo);

        String branchCode =
            String.valueOf(account.get("branch_code"));

        // Block duplicate open loan applications
        Integer duplicate = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM public."MB004010"
            WHERE customer_no = ?
              AND product_code = ?
              AND req_status = 'PENDING'
            """,
            Integer.class,
            customerNo,
            request.productType()
        );

        if (duplicate != null && duplicate > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "An open loan application already exists for this product"
            );
        }

        // Fetch registered mobile
        String mobile = jdbc.queryForObject("""
            SELECT registered_mobile
            FROM public.mb001001cr
            WHERE customer_id = ?
            """,
            String.class,
            customerNo
        );

        // Insert into existing MB004010
        Long requestId = jdbc.queryForObject("""
            INSERT INTO public."MB004010" (
                branch_code,
                customer_no,
                mobile,
                email,
                product_code,
                loan_amount,
                total_income,
                total_exp,
                created_by
            )
            SELECT
                ?,
                ?,
                ?,
                u.email,
                ?,
                ?,
                ?,
                ?,
                ?
            FROM public."MB001001" u
            WHERE u.user_id = ?
            RETURNING id
            """,
            Long.class,
            branchCode,
            customerNo,
            mobile,
            request.productType(),
            request.amount(),
            request.totalIncome(),
            request.householdExpense(),
            userId,
            userId
        );

        Map<String, Object> response = new HashMap<>();

        response.put("requestId", requestId);
        response.put("status", "CREATED");
        response.put("productType", request.productType());
        response.put("amount", request.amount());
        response.put("message",
            "Loan application submitted successfully");

        response.put("tenureMonthsStored", false);
        response.put("purposeStored", false);
        response.put("securityValueStored", false);

        return response;
    }

    // ------------------------------------------------
    // COMMON DATABASE METHODS
    // ------------------------------------------------

    private Long findActiveCustomerUserId(String customerNo) {

        try {
            return jdbc.queryForObject("""
                SELECT user_id
                FROM public."MB001001"
                WHERE customer_no = ?
                  AND user_type = 'CU'
                  AND user_role = 'CUSTOMER'
                  AND status = 'ACTIVE'
                """,
                Long.class,
                customerNo
            );

        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Active customer user was not found"
            );
        }
    }

    private Map<String, Object> findAccount(
            String accountNo,
            String customerNo) {

        try {
            return jdbc.queryForMap("""
                SELECT
                    account_no,
                    customer_no,
                    branch_code,
                    account_type,
                    account_status
                FROM public."MB002003"
                WHERE account_no = ?
                  AND customer_no = ?
                """,
                accountNo,
                customerNo
            );

        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Account not found for logged-in customer"
            );
        }
    }
}
