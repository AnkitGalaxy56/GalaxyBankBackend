package com.abcbank.mobile_banking.service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.request.StandingInstructionRequest;
import com.abcbank.mobile_banking.repository.StandingInstructionRepository;

@Service
public class StandingInstructionService {

    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Kolkata");
    private final StandingInstructionRepository repository;

    public StandingInstructionService(StandingInstructionRepository repository) {
        this.repository = repository;
    }

    private Long userIdFor(String customerNo) {
        try {
            return repository.findActiveCustomerUserId(customerNo);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Active customer user was not found for this login");
        }
    }

    public List<Map<String, Object>> getDebitAccounts(String customerNo) {
        userIdFor(customerNo); // Confirms active customer mapping.
        return repository.findDebitAccounts(customerNo);
    }

    public Map<String, Object> validateCreditAccount(
            String customerNo, String creditAccountId) {
        userIdFor(customerNo);

        Map<String, Object> account = repository.findCreditAccount(creditAccountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Credit account was not found"));

        if (!"ACTIVE".equalsIgnoreCase(String.valueOf(account.get("account_status")))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Credit account is not active");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accountId", account.get("account_no"));
        response.put("accountType", account.get("account_type"));
        response.put("accountStatus", account.get("account_status"));
        response.put("valid", true);
        return response;
    }

    public List<Integer> getTenureOptions(String customerNo) {
        userIdFor(customerNo);
        return java.util.stream.IntStream.rangeClosed(1, 120).boxed().toList();
    }

    public List<Integer> getDebitDays(String customerNo) {
        userIdFor(customerNo);
        return java.util.stream.IntStream.rangeClosed(1, 31).boxed().toList();
    }

    @Transactional
    public Map<String, Object> create(
            String customerNo, StandingInstructionRequest request) {

        Long userId = userIdFor(customerNo);

        Map<String, Object> debit = repository.findDebitAccount(
                request.debitAccountId(), customerNo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Debit account does not belong to the logged-in customer"));

        String debitStatus = String.valueOf(debit.get("account_status"));
        if (!"ACTIVE".equalsIgnoreCase(debitStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Debit account is not active");
        }

        String accountType = String.valueOf(debit.get("account_type"));
        if (!"SAVINGS".equalsIgnoreCase(accountType)
                && !"CURRENT".equalsIgnoreCase(accountType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only Savings and Current accounts can be selected");
        }

        Map<String, Object> credit = repository.findCreditAccount(
                request.creditAccountId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Credit account was not found"));

        String creditStatus = String.valueOf(credit.get("account_status"));
        if (!"ACTIVE".equalsIgnoreCase(creditStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Credit account is not active");
        }

        if (request.debitAccountId().equals(request.creditAccountId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debit and credit accounts cannot be the same");
        }

        LocalDate today = LocalDate.now(BANK_ZONE);
        LocalDate startDate = nextDebitDate(today, request.debitDay());
        LocalDate expiryDate = monthlyDate(
                YearMonth.from(startDate).plusMonths(request.tenure() - 1),
                request.debitDay());

        Long requestId = repository.insertStandingInstruction(
                String.valueOf(debit.get("branch_code")),
                customerNo,
                request.debitAccountId(),
                debitStatus,
                accountType,
                request.amount(),
                request.tenure(),
                Date.valueOf(startDate),
                request.debitDay(),
                Date.valueOf(expiryDate),
                request.creditAccountId(),
                creditStatus,
                userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Standing instruction request submitted successfully");
        response.put("requestId", requestId);
        response.put("debitAccountId", request.debitAccountId());
        response.put("creditAccountId", request.creditAccountId());
        response.put("amount", request.amount());
        response.put("frequency", "MONTHLY");
        response.put("tenure", request.tenure());
        response.put("debitDay", request.debitDay());
        response.put("startDate", startDate);
        response.put("expiryDate", expiryDate);
        response.put("status", "PENDING");
        return response;
    }

    public List<Map<String, Object>> getInstructions(String customerNo) {
        userIdFor(customerNo);
        return repository.findCustomerInstructions(customerNo);
    }

    public Map<String, Object> getInstruction(String customerNo, Long id) {
        userIdFor(customerNo);
        return repository.findCustomerInstruction(id, customerNo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Standing instruction was not found"));
    }

    @Transactional
    public Map<String, Object> cancel(
            String customerNo, Long id) {
        Long userId = userIdFor(customerNo);
        int updated = repository.cancelPendingInstruction(id, customerNo, userId);

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Request was not found or is no longer pending");
        }

        return Map.of(
                "message", "Standing instruction cancelled successfully",
                "requestId", id,
                "status", "CANCELLED");
    }

    private LocalDate nextDebitDate(LocalDate today, int debitDay) {
        LocalDate candidate = monthlyDate(YearMonth.from(today), debitDay);
        if (!candidate.isAfter(today)) {
            candidate = monthlyDate(YearMonth.from(today).plusMonths(1), debitDay);
        }
        return candidate;
    }

    private LocalDate monthlyDate(YearMonth month, int debitDay) {
        return month.atDay(Math.min(debitDay, month.lengthOfMonth()));
    }
}
