
package com.abcbank.mobile_banking.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.abcbank.mobile_banking.dto.request.PositivePayRequest;
import com.abcbank.mobile_banking.dto.response.PositivePayResponse;
import com.abcbank.mobile_banking.service.PositivePayService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/positive-pay")
public class PositivePayController {

    private final PositivePayService service;

    public PositivePayController(
            PositivePayService service) {

        this.service = service;
    }

    // API 1: GET CUSTOMER ACCOUNTS
    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> getAccounts(
            Authentication authentication) {

        return ResponseEntity.ok(
            service.getAccounts(authentication.getName())
        );
    }

    // API 2: VALIDATE CHEQUE
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @Valid @RequestBody PositivePayRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
            service.validate(authentication.getName(), request)
        );
    }

    // API 3: SUBMIT POSITIVE PAY
    @PostMapping("/submit")
    public ResponseEntity<PositivePayResponse> submit(
            @Valid @RequestBody PositivePayRequest request,
            Authentication authentication) {

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                service.submit(authentication.getName(), request)
            );
    }

    // API 4: GET REQUEST DETAILS
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getRequest(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
            service.getRequest(authentication.getName(), id)
        );
    }

    // API 5: REQUEST HISTORY
    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            Authentication authentication) {

        return ResponseEntity.ok(
            service.getHistory(authentication.getName())
        );
    }

    // API 6: CANCEL REQUEST
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @PathVariable Long id,
            Authentication authentication) {

        return ResponseEntity.ok(
            service.cancelRequest(authentication.getName(), id)
        );
    }
}
