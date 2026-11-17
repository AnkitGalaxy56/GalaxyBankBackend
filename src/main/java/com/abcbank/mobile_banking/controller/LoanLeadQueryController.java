
package com.abcbank.mobile_banking.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.abcbank.mobile_banking.service.LoanLeadQueryService;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanLeadQueryController {

    private final LoanLeadQueryService service;

    public LoanLeadQueryController(LoanLeadQueryService service) {
        this.service = service;
    }

    private String getCustomerNo(Authentication authentication) {

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

    // GET: Previously used loan product codes
    @GetMapping("/product-codes")
    public ResponseEntity<Map<String, Object>> getProductCodes(
            Authentication authentication) {

        return ResponseEntity.ok(
                service.getProductCodes(getCustomerNo(authentication))
        );
    }

    // GET: All loan applications of logged-in customer
    @GetMapping("/leads")
    public ResponseEntity<Map<String, Object>> getLoanApplications(
            Authentication authentication) {

        return ResponseEntity.ok(
                service.getLoanApplications(getCustomerNo(authentication))
        );
    }

    // GET: Particular loan application
    @GetMapping("/leads/{id}")
    public ResponseEntity<Map<String, Object>> getLoanApplication(
            Authentication authentication,
            @PathVariable Long id) {

        return ResponseEntity.ok(
                service.getLoanApplication(
                        getCustomerNo(authentication), id
                )
        );
    }

    // GET: Existing loan accounts
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getLoanHistory(
            Authentication authentication) {

        return ResponseEntity.ok(
                service.getLoanHistory(getCustomerNo(authentication))
        );
    }

    // GET: EMI payment request history for a particular loan
    @GetMapping("/history/{loanAccountNo}/emi-requests")
    public ResponseEntity<Map<String, Object>> getEmiRequests(
            Authentication authentication,
            @PathVariable String loanAccountNo) {

        return ResponseEntity.ok(
                service.getEmiRequests(
                        getCustomerNo(authentication),
                        loanAccountNo
                )
        );
    }
}
