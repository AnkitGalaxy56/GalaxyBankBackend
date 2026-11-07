package com.abcbank.mobile_banking.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.abcbank.mobile_banking.dto.request.StandingInstructionRequest;
import com.abcbank.mobile_banking.service.StandingInstructionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/standing-instructions")
public class StandingInstructionController {

    private final StandingInstructionService service;

    public StandingInstructionController(StandingInstructionService service) {
        this.service = service;
    }

    // AuthService.mpinLogin() puts customerId/customer_no in JWT subject.
    private String customerNo(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Login is required");
        }
        return authentication.getName();
    }

    // Debit Account ID dropdown
    @GetMapping("/debit-accounts")
    public ResponseEntity<List<Map<String, Object>>> debitAccounts(
            Authentication authentication) {
        return ResponseEntity.ok(service.getDebitAccounts(customerNo(authentication)));
    }

    // Credit Account ID validation
    @GetMapping("/credit-accounts/{accountId}")
    public ResponseEntity<Map<String, Object>> creditAccount(
            Authentication authentication,
            @PathVariable String accountId) {
        return ResponseEntity.ok(
                service.validateCreditAccount(customerNo(authentication), accountId));
    }

    // Tenure dropdown values in months
    @GetMapping("/tenure-options")
    public ResponseEntity<List<Integer>> tenureOptions(
            Authentication authentication) {
        return ResponseEntity.ok(service.getTenureOptions(customerNo(authentication)));
    }

    // Monthly DD values
    @GetMapping("/debit-days")
    public ResponseEntity<List<Integer>> debitDays(
            Authentication authentication) {
        return ResponseEntity.ok(service.getDebitDays(customerNo(authentication)));
    }

    // Submit Standing Instruction request
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            Authentication authentication,
            @Valid @RequestBody StandingInstructionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(customerNo(authentication), request));
    }

    // Customer's submitted Standing Instructions
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            Authentication authentication) {
        return ResponseEntity.ok(service.getInstructions(customerNo(authentication)));
    }

    // One Standing Instruction detail
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOne(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.ok(service.getInstruction(customerNo(authentication), id));
    }

    // Cancel a PENDING request
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(customerNo(authentication), id));
    }
}
