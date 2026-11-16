
package com.abcbank.mobile_banking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.LockerApplicationRequest;
import com.abcbank.mobile_banking.dto.response.LockerApplicationResponse;
import com.abcbank.mobile_banking.exception.LockerApplicationException;
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

    private String getAuthenticatedCustomerNo(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new LockerApplicationException(
                    "Authentication required");
        }

        return authentication.getName();
    }

    @PostMapping("/apply")
    public ResponseEntity<LockerApplicationResponse> applyLocker(
            @Valid @RequestBody LockerApplicationRequest request,
            Authentication authentication) {

        String customerNo =
                getAuthenticatedCustomerNo(authentication);

        LockerApplicationResponse response =
                service.applyLocker(request, customerNo);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/my-applications")
    public ResponseEntity<List<LockerApplicationResponse>>
            getMyApplications(Authentication authentication) {

        String customerNo =
                getAuthenticatedCustomerNo(authentication);

        return ResponseEntity.ok(
                service.getMyApplications(customerNo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LockerApplicationResponse>
            getApplication(
                    @PathVariable Long id,
                    Authentication authentication) {

        String customerNo =
                getAuthenticatedCustomerNo(authentication);

        return ResponseEntity.ok(
                service.getApplication(id, customerNo));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<LockerApplicationResponse>
            cancelApplication(
                    @PathVariable Long id,
                    Authentication authentication) {

        String customerNo =
                getAuthenticatedCustomerNo(authentication);

        return ResponseEntity.ok(
                service.cancelApplication(id, customerNo));
    }
}
