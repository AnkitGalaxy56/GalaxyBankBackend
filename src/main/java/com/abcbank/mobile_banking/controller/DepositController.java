package com.abcbank.mobile_banking.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.abcbank.mobile_banking.dto.request.CloseDepositRequest;
import com.abcbank.mobile_banking.dto.request.OpenFDRequest;
import com.abcbank.mobile_banking.dto.request.OpenRDRequest;
import com.abcbank.mobile_banking.dto.response.DepositResponse;
import com.abcbank.mobile_banking.service.DepositService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/deposits")
public class DepositController {
    private final DepositService service;
    public DepositController(DepositService service) { this.service = service; }

    private String customerNo(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank())
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Login is required");
        return auth.getName();
    }

    @GetMapping("/schemes/FD")
    public ResponseEntity<List<Map<String,Object>>> fdSchemes(Authentication auth) {
        return ResponseEntity.ok(service.schemes("FD", customerNo(auth)));
    }

    @GetMapping("/schemes/RD")
    public ResponseEntity<List<Map<String,Object>>> rdSchemes(Authentication auth) {
        return ResponseEntity.ok(service.schemes("RD", customerNo(auth)));
    }

    @PostMapping("/fd/open")
    public ResponseEntity<DepositResponse> openFd(Authentication auth,
            @Valid @RequestBody OpenFDRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.openFd(customerNo(auth), request));
    }

    @PostMapping("/rd/open")
    public ResponseEntity<DepositResponse> openRd(Authentication auth,
            @Valid @RequestBody OpenRDRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.openRd(customerNo(auth), request));
    }

    @PostMapping("/close")
    public ResponseEntity<Map<String,Object>> close(Authentication auth,
            @Valid @RequestBody CloseDepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.close(customerNo(auth), request));
    }
}
