package com.abcbank.mobile_banking.exception;

import org.springframework.http.HttpStatus;

/** Login / credential failures that need a specific HTTP status (401, 403, 423). */
public class AuthException extends RuntimeException {
    private final HttpStatus status;

    public AuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
