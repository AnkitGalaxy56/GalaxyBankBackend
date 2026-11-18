
package com.abcbank.mobile_banking.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.BeneficiaryRequest;
import com.abcbank.mobile_banking.dto.request.DeleteBeneficiaryRequest;
import com.abcbank.mobile_banking.dto.request.UpdateBeneficiaryRequest;
import com.abcbank.mobile_banking.dto.request.VerifyBeneficiaryOtpRequest;
import com.abcbank.mobile_banking.dto.response.BeneficiaryResponse;
import com.abcbank.mobile_banking.repository.BeneficiaryRepository;
import com.abcbank.mobile_banking.service.AuthService;
import com.abcbank.mobile_banking.service.BeneficiaryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService service;
    private final BeneficiaryRepository repository;
    private final AuthService authService;

    public BeneficiaryController(
            BeneficiaryService service,
            BeneficiaryRepository repository,
            AuthService authService) {
        this.service = service;
        this.repository = repository;
        this.authService = authService;
    }

    // ---------------------------------------------------------
    // ADD BENEFICIARY
    // POST /api/v1/beneficiaries
    // ---------------------------------------------------------

    @PostMapping
    public ResponseEntity<Map<String, Object>> addBeneficiary(
            Authentication authentication,
            @Valid @RequestBody BeneficiaryRequest request) {

        Map<String, Object> customer =
                repository.customerByPrincipal(authentication.getName());

        String customerNo = String.valueOf(customer.get("customer_no"));
        Long userId = ((Number) customer.get("user_id")).longValue();

        String message = service.addBeneficiary(
                request, customerNo, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", message));
    }

    // ---------------------------------------------------------
    // VERIFY BENEFICIARY OTP
    // POST /api/v1/beneficiaries/verify-otp
    // ---------------------------------------------------------

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
                request.otp());

        return ResponseEntity.ok(Map.of("message", message));
    }

    // ---------------------------------------------------------
    // GET ALL BENEFICIARIES
    // GET /api/v1/beneficiaries
    // ---------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> getBeneficiaries(
            Authentication authentication) {

        Map<String, Object> customer =
                repository.customerByPrincipal(authentication.getName());

        String customerNo = String.valueOf(customer.get("customer_no"));

        return ResponseEntity.ok(
                service.getBeneficiaries(customerNo));
    }

    // ---------------------------------------------------------
    // UPDATE BENEFICIARY
    // PUT /api/v1/beneficiaries/{id}
    // MPIN verification required
    // ---------------------------------------------------------

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateBeneficiary(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateBeneficiaryRequest request) {

        Map<String, Object> customer =
                repository.customerByPrincipal(authentication.getName());

        String customerNo = String.valueOf(customer.get("customer_no"));
        Long userId = ((Number) customer.get("user_id")).longValue();

        // Verify customer MPIN before updating
        authService.verifyMpinForSensitiveAction(
                userId, request.mpin());

        String message = service.updateBeneficiary(
                id,
                customerNo,
                request.beneficiaryName(),
                request.singleTransactionLimit());

        return ResponseEntity.ok(Map.of("message", message));
    }

    // ---------------------------------------------------------
    // DELETE BENEFICIARY
    // DELETE /api/v1/beneficiaries/{id}
    // MPIN verification required
    // Soft delete: status becomes CANCELLED
    // ---------------------------------------------------------

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteBeneficiary(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody DeleteBeneficiaryRequest request) {

        Map<String, Object> customer =
                repository.customerByPrincipal(authentication.getName());

        String customerNo = String.valueOf(customer.get("customer_no"));
        Long userId = ((Number) customer.get("user_id")).longValue();

        // Verify customer MPIN before deleting
        authService.verifyMpinForSensitiveAction(
                userId, request.mpin());

        String message = service.deactivateBeneficiary(
                id, customerNo);

        return ResponseEntity.ok(Map.of("message", message));
    }
}
