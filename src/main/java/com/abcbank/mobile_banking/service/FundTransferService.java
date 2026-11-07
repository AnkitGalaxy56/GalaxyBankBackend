
package com.abcbank.mobile_banking.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abcbank.mobile_banking.dto.request.FundTransferRequest;
import com.abcbank.mobile_banking.dto.response.FundTransferResponse;
import com.abcbank.mobile_banking.entity.FundTransferRequestEntity;
import com.abcbank.mobile_banking.repository.FundTransferRequestRepository;

@Service
public class FundTransferService {

    private final JdbcTemplate jdbc;
    private final FundTransferRequestRepository repository;

    public FundTransferService(
            JdbcTemplate jdbc,
            FundTransferRequestRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

    @Transactional
    public FundTransferResponse submit(
            String customerId,
            FundTransferRequest request) {

        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Invalid customer authentication.");
        }

        // 1. Confirm beneficiary account numbers
        String beneficiaryAccount = request.accountNo().trim();
        String confirmAccount = request.reConfirmAccountNo().trim();

        if (!beneficiaryAccount.equals(confirmAccount)) {
            throw new IllegalArgumentException(
                    "Beneficiary account numbers do not match.");
        }

        // 2. Normalize payment mode and IFSC
        String mode = request.paymentMode().trim()
                .toUpperCase(Locale.ROOT);

        String ifsc = request.ifscCode().trim()
                .toUpperCase(Locale.ROOT);

        // 3. RTGS minimum amount validation
        if ("RTGS".equals(mode)
                && request.amount().compareTo(
                        new BigDecimal("200000.00")) < 0) {

            throw new IllegalArgumentException(
                    "RTGS minimum amount is Rs. 2,00,000. "
                    + "Please select NEFT or IMPS.");
        }

        // 4. Validate logged-in customer
        Long createdBy;

        try {
            createdBy = jdbc.queryForObject("""
                SELECT user_id
                FROM public."MB001001"
                WHERE customer_no = ?
                  AND user_type = 'CU'
                  AND status = 'ACTIVE'
                """, Long.class, customerId);

        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException(
                    "Active customer user record not found.");
        }

        // 5. Validate debit account ownership and status
        Map<String, Object> debit;

        try {
            debit = jdbc.queryForMap("""
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
                request.debitAccountId().trim(),
                customerId);

        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException(
                    "Debit account not found or does not belong to you.");
        }

        String accountStatus =
                String.valueOf(debit.get("account_status"));

        if (!"ACTIVE".equalsIgnoreCase(accountStatus)) {
            throw new IllegalArgumentException(
                    "Debit account is not active.");
        }

        // 6. Validate beneficiary IFSC and get bank name
        Map<String, Object> beneficiary;

        try {
            beneficiary = jdbc.queryForMap("""
                SELECT
                    b.branch_code,
                    b.bank_code,
                    b.branch_name,
                    b.ifsc_code,
                    bank.bank_name
                FROM public."MB002002" b
                JOIN public."MB002001" bank
                  ON bank.bank_code = b.bank_code
                WHERE UPPER(b.ifsc_code) = ?
                """, ifsc);

        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException(
                    "Invalid beneficiary IFSC code.");
        }

        String bankName =
                String.valueOf(beneficiary.get("bank_name"));

        // 7. Save transfer request
        FundTransferRequestEntity entity =
                new FundTransferRequestEntity(
                        String.valueOf(debit.get("branch_code")),
                        customerId,
                        String.valueOf(debit.get("account_no")),
                        accountStatus,
                        String.valueOf(debit.get("account_type")),
                        mode,
                        request.beneficiaryName().trim(),
                        beneficiaryAccount,
                        bankName,
                        ifsc,
                        request.amount(),
                        request.remarks(),
                        createdBy
                );

        FundTransferRequestEntity saved = repository.save(entity);

        // 8. Return response
        return new FundTransferResponse(
                saved.getId(),
                saved.getReqStatus(),
                mode,
               request.amount(),
                saved.getCreatedOn(),
                "Transfer request submitted successfully. "
                + "Awaiting bank processing."
        );
    }
}
