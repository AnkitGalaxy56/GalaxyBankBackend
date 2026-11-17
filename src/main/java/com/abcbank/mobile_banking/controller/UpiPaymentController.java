
package com.abcbank.mobile_banking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.UpiQrDecodeRequest;
import com.abcbank.mobile_banking.dto.response.UpiQrDecodeResponse;
import com.abcbank.mobile_banking.service.UpiQrService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/payments/upi")
public class UpiPaymentController {

    private final UpiQrService service;

    public UpiPaymentController(UpiQrService service) {
        this.service = service;
    }

    @PostMapping("/qr/decode")
    public ResponseEntity<UpiQrDecodeResponse> decode(
            @Valid @RequestBody UpiQrDecodeRequest request,
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException(
                "Customer authentication required.");
        }

        return ResponseEntity.ok(
            service.decode(request.qrPayload())
        );
    }
}
