package com.abcbank.mobile_banking.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.FundTransferRequest;
import com.abcbank.mobile_banking.dto.response.FundTransferResponse;
import com.abcbank.mobile_banking.dto.response.TransferStatusResponse;
import com.abcbank.mobile_banking.service.FundTransferService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transfers")
public class FundTransferController {

    private final FundTransferService service;

    public FundTransferController(FundTransferService service) {
        this.service = service;
    }

    // POST: Submit NEFT / RTGS / IMPS request
    @PostMapping("/rtgs-neft-imps")
    public ResponseEntity<FundTransferResponse> submit(
            @Valid @RequestBody FundTransferRequest request,
            Authentication authentication) {

        FundTransferResponse response =
                service.submit(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }
    // POST: Confirm transfer request
@PostMapping("/{requestId}/confirm")
public ResponseEntity<Map<String, Object>> confirmTransfer(
        @PathVariable Long requestId,
        Authentication authentication) {

    Map<String, Object> response =
            service.confirmTransfer(
                    authentication.getName(),
                    requestId
            );

    return ResponseEntity.ok(response);
}

    // GET: Check transfer status
    @GetMapping("/{requestId}")
    public ResponseEntity<TransferStatusResponse> getTransferStatus(
            @PathVariable Long requestId,
            Authentication authentication) {

        TransferStatusResponse response =
                service.getTransferStatus(
                        authentication.getName(),
                        requestId
                );

        return ResponseEntity.ok(response);
    }
}