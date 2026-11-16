package com.abcbank.mobile_banking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.UpdateProfileRequest;
import com.abcbank.mobile_banking.dto.response.ProfileResponse;
import com.abcbank.mobile_banking.service.ProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    /** Profile card shown on the screen. Customer comes from the JWT, never from the client. */
    @GetMapping
    public ResponseEntity<ProfileResponse> get(Authentication auth) {
        return ResponseEntity.ok(service.get(auth.getName()));
    }

    /** "Edit profile" save button. */
    @PutMapping
    public ResponseEntity<ProfileResponse> update(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication auth) {
        return ResponseEntity.ok(service.update(auth.getName(), request));
    }
}
