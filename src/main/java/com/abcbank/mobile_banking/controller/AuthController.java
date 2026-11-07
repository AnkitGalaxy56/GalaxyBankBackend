package com.abcbank.mobile_banking.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.request.ForgotMpinRequest;
import com.abcbank.mobile_banking.dto.request.MpinLoginRequest;
import com.abcbank.mobile_banking.dto.request.RegistrationCompleteRequest;
import com.abcbank.mobile_banking.dto.request.VerifyMpinResetOtpRequest;
import com.abcbank.mobile_banking.dto.request.WebLoginRequest;
import com.abcbank.mobile_banking.dto.response.AuthResponse;
import com.abcbank.mobile_banking.dto.response.MessageResponse;
import com.abcbank.mobile_banking.dto.response.RegistrationStartResponse;
import com.abcbank.mobile_banking.dto.response.RegistrationTokenResponse;
import com.abcbank.mobile_banking.service.AuthService;
import com.abcbank.mobile_banking.service.MpinResetService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final MpinResetService mpinResetService;

    public AuthController(AuthService authService, MpinResetService mpinResetService) {
        this.authService = authService;
        this.mpinResetService = mpinResetService;
    }

    /** Web page: username + password. */
    @PostMapping("/web/login")
    public ResponseEntity<AuthResponse> webLogin(@Valid @RequestBody WebLoginRequest request) {
        return ResponseEntity.ok(authService.webLogin(request));
    }




    /** Mobile app: customerId + 4-digit MPIN. */
    @PostMapping("/mpin/login")
    public ResponseEntity<AuthResponse> mpinLogin(@Valid @RequestBody MpinLoginRequest request) {
        return ResponseEntity.ok(authService.mpinLogin(request));
    }

    /** "Forgot MPIN": sends a 4-digit OTP to the registered mobile. */
    @PostMapping("/forgot-mpin/request")
    public ResponseEntity<RegistrationStartResponse> forgotMpin(@Valid @RequestBody ForgotMpinRequest request) {
        return ResponseEntity.accepted().body(mpinResetService.requestOtp(request));
    }

    /** "Submit OTP": returns a short-lived token for the new-MPIN screen. */
    @PostMapping("/forgot-mpin/verify-otp")
    public ResponseEntity<RegistrationTokenResponse> verifyResetOtp(
            @Valid @RequestBody VerifyMpinResetOtpRequest request) {
        return ResponseEntity.ok(mpinResetService.verifyOtp(request));
    }

    /** New-MPIN screen: needs "Authorization: Bearer <token from verify-otp>". */
    @PostMapping("/forgot-mpin/reset")
    public ResponseEntity<MessageResponse> resetMpin(
            @Valid @RequestBody RegistrationCompleteRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(mpinResetService.resetMpin(authentication.getName(), request));
    }
}
