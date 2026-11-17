
package com.abcbank.mobile_banking.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.abcbank.mobile_banking.dto.request.NomineeRequest;
import com.abcbank.mobile_banking.service.NomineeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/nominees")
public class NomineeController {

    private final NomineeService service;

    public NomineeController(NomineeService service) {
        this.service = service;
    }

    // Add nominee
    @PostMapping
    public ResponseEntity<Map<String, Object>> addNominee(
            Authentication auth,
            @Valid @RequestBody NomineeRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.add(auth.getName(), request));
    }

    // Get all nominees
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllNominees(
            Authentication auth,
            @RequestParam String accountNo) {

        return ResponseEntity.ok(
            service.getAll(auth.getName(), accountNo)
        );
    }

    // Get nominee by ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getNominee(
            Authentication auth,
            @PathVariable Long id) {

        return ResponseEntity.ok(
            service.getOne(auth.getName(), id)
        );
    }

    // Update nominee
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateNominee(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody NomineeRequest request) {

        return ResponseEntity.ok(
            service.update(auth.getName(), id, request)
        );
    }

    // Delete nominee
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteNominee(
            Authentication auth,
            @PathVariable Long id) {

        return ResponseEntity.ok(
            service.delete(auth.getName(), id)
        );
    }
}
