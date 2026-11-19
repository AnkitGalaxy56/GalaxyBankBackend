
package com.abcbank.mobile_banking.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.request.FundTransferRequest;
import com.abcbank.mobile_banking.dto.response.FundTransferResponse;
import com.abcbank.mobile_banking.dto.response.TransferStatusResponse;
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
            throw new IllegalArgumentException(
                    "Invalid customer authentication.");
        }

        String beneficiaryAccount = request.accountNo().trim();
        String confirmAccount = request.reConfirmAccountNo().trim();

        if (!beneficiaryAccount.equals(confirmAccount)) {
            throw new IllegalArgumentException(
                    "Beneficiary account numbers do not match.");
        }

        String mode = request.paymentMode().trim()
                .toUpperCase(Locale.ROOT);

        String ifsc = request.ifscCode().trim()
                .toUpperCase(Locale.ROOT);

        if ("RTGS".equals(mode)
                && request.amount().compareTo(
                        new BigDecimal("200000.00")) < 0) {
            throw new IllegalArgumentException(
                    "RTGS minimum amount is Rs. 2,00,000. "
                    + "Please select NEFT or IMPS.");
        }

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

        entity.setIsSys(true);

        FundTransferRequestEntity saved =
                repository.save(entity);

        Integer requestSource = Boolean.TRUE.equals(saved.getIsSys())
                ? 1 : 2;

        return new FundTransferResponse(
                saved.getId(),
                saved.getReqStatus(),
                mode,
                request.amount(),
                saved.getCreatedOn(),
                requestSource,
                "Transfer request submitted successfully. "
                + "Awaiting bank processing."
        );
    }

    // GET: Check transfer status
    public TransferStatusResponse getTransferStatus(
            String customerId,
            Long requestId) {

        if (customerId == null || customerId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid customer authentication.");
        }

        FundTransferRequestEntity transfer = repository
                .findByIdAndCustomerNo(requestId, customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Transfer request not found."));

        String databaseStatus = transfer.getReqStatus();

        String apiStatus = switch (databaseStatus.toUpperCase()) {
            case "PENDING" -> "SUBMITTED";
            case "APPROVED" -> "PROCESSING";
            case "COMPLETED" -> "SUCCESS";
            case "FAILED", "REJECTED" -> "FAILED";
            case "CANCELLED" -> "REVERSED";
            default -> databaseStatus;
        };

        return new TransferStatusResponse(
                transfer.getId(),
                apiStatus,
                transfer.getAmount(),
                transfer.getPayMode(),
                null,
                transfer.getCreatedOn());
    }

    // POST: Customer confirms a submitted transfer request
    @Transactional
    public Map<String, Object> confirmTransfer(
            String customerId,
            Long requestId) {

        if (customerId == null || customerId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid customer authentication.");
        }

        // Find the request belonging to this customer
        Map<String, Object> transfer;

        try {
            transfer = jdbc.queryForMap("""
                SELECT
                    id,
                    req_status,
                    customer_confirmed_on
                FROM public."MB004001"
                WHERE id = ?
                  AND customer_no = ?
                FOR UPDATE
                """, requestId, customerId);

        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Transfer request not found.");
        }

        String status =
                String.valueOf(transfer.get("req_status"));

        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending transfer requests can be confirmed.");
        }

        if (transfer.get("customer_confirmed_on") != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transfer request has already been confirmed.");
        }

        // Get the logged-in customer's internal user ID
        Long confirmedBy;

        try {
            confirmedBy = jdbc.queryForObject("""
                SELECT user_id
                FROM public."MB001001"
                WHERE customer_no = ?
                  AND user_type = 'CU'
                  AND status = 'ACTIVE'
                """, Long.class, customerId);

        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Active customer user record not found.");
        }

        // Record confirmation. Do not change req_status.
        int updated = jdbc.update("""
            UPDATE public."MB004001"
            SET customer_confirmed_by = ?,
                customer_confirmed_on = CURRENT_TIMESTAMP
            WHERE id = ?
              AND customer_no = ?
              AND req_status = 'PENDING'
              AND customer_confirmed_on IS NULL
            """, confirmedBy, requestId, customerId);

        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transfer request could not be confirmed. "
                    + "Please refresh and try again.");
        }

        return Map.of(
                "success", true,
                "message", "Transfer request confirmed successfully.",
                "requestId", requestId,
                "status", "PENDING",
                "confirmed", true
        );
    }
}
