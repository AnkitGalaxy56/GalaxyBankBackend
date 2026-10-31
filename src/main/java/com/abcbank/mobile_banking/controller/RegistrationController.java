package com.abcbank.mobile_banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.RegistrationCompleteRequest;
import com.abcbank.mobile_banking.dto.request.RegistrationStartRequest;
import com.abcbank.mobile_banking.dto.request.VerifyRegistrationOtpRequest;
import com.abcbank.mobile_banking.dto.response.RegistrationCompleteResponse;
import com.abcbank.mobile_banking.dto.response.RegistrationStartResponse;
import com.abcbank.mobile_banking.dto.response.RegistrationTokenResponse;
import com.abcbank.mobile_banking.service.RegistrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/registration")
public class RegistrationController {

    private final RegistrationService service;

    public RegistrationController(RegistrationService service) {
        this.service = service;
    }

    @PostMapping("/start")
    public ResponseEntity<RegistrationStartResponse> start(
            @Valid @RequestBody RegistrationStartRequest request) {

        return ResponseEntity.accepted().body(
                service.start(request)
        );
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<RegistrationTokenResponse> verify(
            @Valid @RequestBody VerifyRegistrationOtpRequest request) {

        return ResponseEntity.ok(service.verify(request));
    }

    @PostMapping("/complete")
    public ResponseEntity<RegistrationCompleteResponse> complete(
            @Valid @RequestBody RegistrationCompleteRequest request,
            Authentication authentication) {

        String customerId = authentication.getName();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.complete(customerId, request)
        );
    }
}