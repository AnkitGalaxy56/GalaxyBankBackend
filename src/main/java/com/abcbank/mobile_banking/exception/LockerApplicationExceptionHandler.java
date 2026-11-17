
package com.abcbank.mobile_banking.exception;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class LockerApplicationExceptionHandler {

    @ExceptionHandler(LockerApplicationException.class)
    public ResponseEntity<Map<String, Object>> handleLockerException(
            LockerApplicationException ex) {

        return ResponseEntity.badRequest().body(
                Map.of(
                        "success", false,
                        "message", ex.getMessage()
                )
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request");

        return ResponseEntity.badRequest().body(
                Map.of(
                        "success", false,
                        "message", message
                )
        );
    }
}
