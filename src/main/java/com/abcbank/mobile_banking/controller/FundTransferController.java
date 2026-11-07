
package com.abcbank.mobile_banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.FundTransferRequest;
import com.abcbank.mobile_banking.dto.response.FundTransferResponse;
import com.abcbank.mobile_banking.service.FundTransferService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transfers")
public class FundTransferController {

    private final FundTransferService service;

    public FundTransferController(FundTransferService service) {
        this.service = service;
    }

    @PostMapping("/rtgs-neft-imps")
    public ResponseEntity<FundTransferResponse> submit(
            @Valid @RequestBody FundTransferRequest request,
            Authentication authentication) {

        FundTransferResponse response =
                service.submit(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
