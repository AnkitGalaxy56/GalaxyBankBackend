
package com.abcbank.mobile_banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.StopPaymentRequest;
import com.abcbank.mobile_banking.dto.response.StopPaymentResponse;
import com.abcbank.mobile_banking.service.StopPaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/requests/stop-payment")
public class StopPaymentController {

    private final StopPaymentService service;

    public StopPaymentController(
            StopPaymentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<StopPaymentResponse> submit(
            @Valid @RequestBody StopPaymentRequest request,
            Authentication authentication) {

        StopPaymentResponse response = service.submit(
            authentication.getName(),
            request
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
}
