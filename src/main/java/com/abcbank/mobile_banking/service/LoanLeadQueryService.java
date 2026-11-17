
package com.abcbank.mobile_banking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanLeadQueryService {

    private final JdbcTemplate jdbc;

    public LoanLeadQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------
    // LON-07: GET PREVIOUSLY USED PRODUCT CODES
    // ------------------------------------------------

    public Map<String, Object> getProductCodes(String customerNo) {

        List<Map<String, Object>> products = jdbc.queryForList("""
            SELECT DISTINCT product_code
            FROM public."MB004010"
            WHERE customer_no = ?
              AND product_code IS NOT NULL
            ORDER BY product_code
            """, customerNo);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("customerNo", customerNo);
        response.put("productCodes", products);
        response.put("count", products.size());
        response.put("message",
                "Previously used loan product codes retrieved successfully");

        return response;
    }

    // ------------------------------------------------
    // LON-08: GET ALL LOAN APPLICATIONS
    // ------------------------------------------------

    public Map<String, Object> getLoanApplications(String customerNo) {

        List<Map<String, Object>> applications = jdbc.queryForList("""
            SELECT
                id AS request_id,
                customer_no,
                branch_code,
                mobile,
                email,
                product_code,
                loan_amount,
                total_income,
                total_exp,
                eligibility,
                req_status,
                created_on,
                modified_on
            FROM public."MB004010"
            WHERE customer_no = ?
            ORDER BY created_on DESC
            """, customerNo);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("customerNo", customerNo);
        response.put("applications", applications);
        response.put("count", applications.size());
        response.put("message",
                "Loan applications retrieved successfully");

        return response;
    }

    // ------------------------------------------------
    // LON-09: GET LOAN APPLICATION BY ID
    // ------------------------------------------------

    public Map<String, Object> getLoanApplication(
            String customerNo, Long id) {

        try {

            Map<String, Object> application = jdbc.queryForMap("""
                SELECT
                    id AS request_id,
                    customer_no,
                    branch_code,
                    mobile,
                    email,
                    product_code,
                    loan_amount,
                    total_income,
                    total_exp,
                    eligibility,
                    req_status,
                    created_on,
                    modified_on
                FROM public."MB004010"
                WHERE id = ?
                  AND customer_no = ?
                """, id, customerNo);

            Map<String, Object> response = new LinkedHashMap<>();

            response.put("application", application);
            response.put("message",
                    "Loan application retrieved successfully");

            return response;

        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Loan application not found"
            );
        }
    }

    // ------------------------------------------------
    // LON-10: GET EXISTING LOAN HISTORY
    // ------------------------------------------------

    public Map<String, Object> getLoanHistory(String customerNo) {

        List<Map<String, Object>> loans = jdbc.queryForList("""
            SELECT
                a.account_no AS loan_account_no,
                a.customer_no,
                a.branch_code,
                a.account_type,
                a.account_status,
                a.created_on,

                e.emi_amount AS last_emi_payment_request_amount,
                e.req_status AS last_emi_request_status,
                e.created_on AS last_emi_request_date

            FROM public."MB002003" a

            LEFT JOIN LATERAL (
                SELECT
                    p.emi_amount,
                    p.req_status,
                    p.created_on
                FROM public."MB004006" p
                WHERE p.customer_no = a.customer_no
                  AND p.loan_account_no = a.account_no
                ORDER BY p.created_on DESC
                LIMIT 1
            ) e ON TRUE

            WHERE a.customer_no = ?
              AND UPPER(a.account_type) = 'LOAN'

            ORDER BY a.created_on DESC
            """, customerNo);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("customerNo", customerNo);
        response.put("loanAccounts", loans);
        response.put("count", loans.size());
        response.put("message",
                "Existing loan accounts retrieved successfully");

        return response;
    }

    // ------------------------------------------------
    // LON-11: GET EMI PAYMENT REQUEST HISTORY
    // ------------------------------------------------

    public Map<String, Object> getEmiRequests(
            String customerNo,
            String loanAccountNo) {

        // First verify that the loan belongs to this customer.
        Integer accountCount = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM public."MB002003"
            WHERE account_no = ?
              AND customer_no = ?
              AND UPPER(account_type) = 'LOAN'
            """,
            Integer.class,
            loanAccountNo,
            customerNo
        );

        if (accountCount == null || accountCount == 0) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Loan account not found for logged-in customer"
            );
        }

        List<Map<String, Object>> requests = jdbc.queryForList("""
            SELECT
                id AS request_id,
                loan_account_no,
                emi_amount,
                req_status,
                created_on
            FROM public."MB004006"
            WHERE customer_no = ?
              AND loan_account_no = ?
            ORDER BY created_on DESC
            """,
            customerNo,
            loanAccountNo
        );

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("loanAccountNo", loanAccountNo);
        response.put("emiPaymentRequests", requests);
        response.put("count", requests.size());
        response.put("message",
                "EMI payment request history retrieved successfully");

        return response;
    }
}
