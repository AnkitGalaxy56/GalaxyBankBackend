
package com.abcbank.mobile_banking.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.EmiPaymentRequest;
import com.abcbank.mobile_banking.dto.request.LoanLeadRequest;
import com.abcbank.mobile_banking.service.LoanPaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class LoanPaymentController {

    private final LoanPaymentService service;

    public LoanPaymentController(LoanPaymentService service) {
        this.service = service;
    }

    private String customerNo(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {

            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Customer login is required"
            );
        }

        return authentication.getName();
    }

    // LON-03: Create EMI payment request
    @PostMapping("/loans/{loanId}/emi-payments")
    public ResponseEntity<Map<String, Object>> createEmiPayment(
            Authentication authentication,
            @PathVariable String loanId,
            @Valid @RequestBody EmiPaymentRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
            service.createEmiPayment(
                customerNo(authentication),
                loanId,
                request
            )
        );
    }

    // LON-06: Apply for loan
    @PostMapping("/loans/leads")
    public ResponseEntity<Map<String, Object>> createLoanLead(
            Authentication authentication,
            @Valid @RequestBody LoanLeadRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
            service.createLoanLead(
                customerNo(authentication),
                request
            )
        );
    }
}
