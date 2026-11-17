
package com.abcbank.mobile_banking.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.abcbank.mobile_banking.dto.request.BeneficiaryRequest;
import com.abcbank.mobile_banking.dto.request.VerifyBeneficiaryOtpRequest;
import com.abcbank.mobile_banking.dto.response.BeneficiaryResponse;
import com.abcbank.mobile_banking.repository.BeneficiaryRepository;
import com.abcbank.mobile_banking.service.BeneficiaryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService service;
    private final BeneficiaryRepository repository;

    public BeneficiaryController(
            BeneficiaryService service,
            BeneficiaryRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    // Add beneficiary and send OTP
    @PostMapping
    public ResponseEntity<Map<String, Object>> addBeneficiary(
            Authentication authentication,
            @Valid @RequestBody BeneficiaryRequest request) {

        Map<String, Object> customer =
                repository.customerByPrincipal(authentication.getName());

        String customerNo = String.valueOf(customer.get("customer_no"));
        Long userId = ((Number) customer.get("user_id")).longValue();

        String message = service.addBeneficiary(
                request,
                customerNo,
                userId
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", message));
    }

    // Verify beneficiary OTP
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyBeneficiaryOtp(
            Authentication authentication,
            @Valid @RequestBody VerifyBeneficiaryOtpRequest request) {

        Map<String, Object> customer =
                repository.customerByPrincipal(authentication.getName());

        String customerNo = String.valueOf(customer.get("customer_no"));

        String message = service.verifyBeneficiaryOtp(
                request.beneficiaryId(),
                customerNo,
                request.otp()
        );

        return ResponseEntity.ok(Map.of("message", message));
    }

    // Get logged-in customer's beneficiaries
    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> getBeneficiaries(
            Authentication authentication) {

        Map<String, Object> customer =
                repository.customerByPrincipal(authentication.getName());

        String customerNo = String.valueOf(customer.get("customer_no"));

        return ResponseEntity.ok(
                service.getBeneficiaries(customerNo)
        );
    }

    // Deactivate beneficiary
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deactivateBeneficiary(
            Authentication authentication,
            @PathVariable Long id) {

        Map<String, Object> customer =
                repository.customerByPrincipal(authentication.getName());

        String customerNo = String.valueOf(customer.get("customer_no"));

        String message = service.deactivateBeneficiary(id, customerNo);

        return ResponseEntity.ok(Map.of("message", message));
    }
}
