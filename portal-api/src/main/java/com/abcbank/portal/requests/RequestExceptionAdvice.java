package com.abcbank.portal.requests;

import com.abcbank.portal.controller.DeviceResetController;
import com.abcbank.portal.controller.MastersController;
import com.abcbank.portal.controller.MobileImagesController;
import com.abcbank.portal.controller.PushNotificationsController;
import com.abcbank.portal.controller.ThemeController;
import com.abcbank.portal.controller.RequestHandlingController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Error handling for the request-handling API only (other controllers are unaffected).
 * Messages are always client-safe: SQL text and stack traces never reach the response.
 */
@RestControllerAdvice(assignableTypes = {RequestHandlingController.class, MastersController.class, MobileImagesController.class, PushNotificationsController.class, DeviceResetController.class, ThemeController.class})
public class RequestExceptionAdvice {
    private static final Logger log = LoggerFactory.getLogger(RequestExceptionAdvice.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", e.getMessage());
        if (!e.errors().isEmpty()) body.put("errors", e.errors());
        return ResponseEntity.status(e.status()).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Request body is missing or is not valid JSON."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> data(DataAccessException e) {
        String ref = UUID.randomUUID().toString().substring(0, 8);
        log.error("Database error [ref {}]", ref, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "The request could not be saved. Reference: " + ref));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception e) throws Exception {
        if (e instanceof org.springframework.security.access.AccessDeniedException
                || e instanceof org.springframework.security.core.AuthenticationException
                || e instanceof org.springframework.web.server.ResponseStatusException) {
            throw e; // let the framework handle auth and routing errors
        }
        String ref = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unexpected error [ref {}]", ref, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Unexpected server error. Reference: " + ref));
    }
}
