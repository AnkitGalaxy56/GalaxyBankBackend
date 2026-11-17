
package com.abcbank.mobile_banking.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.abcbank.mobile_banking.service.KycService;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {

    private final KycService service;

    public KycController(KycService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> uploadDocument(
            Authentication authentication,
            @RequestParam String accountNo,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(
                service.uploadDocument(
                        authentication.getName(),
                        accountNo,
                        documentType,
                        file
                )
        );
    }

    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> getDocuments(
            Authentication authentication,
            @RequestParam String accountNo) {

        return ResponseEntity.ok(
                service.getDocuments(authentication.getName(), accountNo)
        );
    }

    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getProgress(
            Authentication authentication,
            @RequestParam String accountNo) {

        return ResponseEntity.ok(
                service.getProgress(authentication.getName(), accountNo)
        );
    }

    @PostMapping("/verify-details")
    public ResponseEntity<Map<String, Object>> verifyDetails(
            Authentication authentication,
            @RequestParam String accountNo) {

        return ResponseEntity.ok(
                service.verifyDetails(authentication.getName(), accountNo)
        );
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitKyc(
            Authentication authentication,
            @RequestParam String accountNo) {

        return ResponseEntity.ok(
                service.submitKyc(authentication.getName(), accountNo)
        );
    }
}
