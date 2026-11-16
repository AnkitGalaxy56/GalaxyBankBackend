package com.abcbank.portal.requests;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Error with a client-safe message and optional field-level errors. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, String> errors;

    public ApiException(HttpStatus status, String message) { this(status, message, Map.of()); }

    public ApiException(HttpStatus status, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.errors = errors;
    }

    public HttpStatus status() { return status; }
    public Map<String, String> errors() { return errors; }
}
