
package com.abcbank.mobile_banking.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.LockerApplicationRequest;
import com.abcbank.mobile_banking.service.LockerApplicationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/locker")
public class LockerApplicationController {

    private final LockerApplicationService service;

    public LockerApplicationController(
            LockerApplicationService service) {
        this.service = service;
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply(
            Authentication authentication,
            @Valid @RequestBody LockerApplicationRequest request) {

        Map<String, Object> response = service.apply(
                authentication.getName(),
                request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
