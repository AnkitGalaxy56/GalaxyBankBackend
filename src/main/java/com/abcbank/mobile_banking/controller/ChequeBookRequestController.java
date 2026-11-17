
package com.abcbank.mobile_banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.ChequeBookRequest;
import com.abcbank.mobile_banking.dto.response.ChequeBookResponse;
import com.abcbank.mobile_banking.service.ChequeBookRequestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/cheque-book-requests")
public class ChequeBookRequestController {

    private final ChequeBookRequestService service;

    public ChequeBookRequestController(
            ChequeBookRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ChequeBookResponse> submit(
            @Valid @RequestBody ChequeBookRequest request,
            Authentication authentication) {

        ChequeBookResponse response = service.submit(
            authentication.getName(),
            request
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }
}
