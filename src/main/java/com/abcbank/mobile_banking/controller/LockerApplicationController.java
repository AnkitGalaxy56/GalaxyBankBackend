
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

    private Long getAuthenticatedUserId(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new LockerApplicationException(
                    "Authentication required");
        }

        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new LockerApplicationException(
                    "JWT subject is not a numeric user ID. "
                    + "Configure the user ID from your JWT principal.");
        }
    }

    // POST: Apply for locker

    @PostMapping("/apply")
    public ResponseEntity<LockerApplicationResponse> applyLocker(
            @Valid @RequestBody LockerApplicationRequest request,
            Authentication authentication) {

        Long userId = getAuthenticatedUserId(authentication);

        LockerApplicationResponse response =
                service.applyLocker(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // GET: View my applications

    @GetMapping("/my-applications")
    public ResponseEntity<List<LockerApplicationResponse>>
            getMyApplications(Authentication authentication) {

        Long userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(
                service.getMyApplications(userId));
    }

    // GET: View application by ID

    @GetMapping("/{id}")
    public ResponseEntity<LockerApplicationResponse>
            getApplication(
                    @PathVariable Long id,
                    Authentication authentication) {

        Long userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(
                service.getApplication(id, userId));
    }

    // PUT: Cancel application

    @PutMapping("/{id}/cancel")
    public ResponseEntity<LockerApplicationResponse>
            cancelApplication(
                    @PathVariable Long id,
                    Authentication authentication) {

        Long userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(
                service.cancelApplication(id, userId));
    }
}
