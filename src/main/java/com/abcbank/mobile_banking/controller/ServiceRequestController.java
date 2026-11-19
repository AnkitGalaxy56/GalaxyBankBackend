
package com.abcbank.mobile_banking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.response.ServiceRequestListResponse;
import com.abcbank.mobile_banking.service.ServiceRequestService;

@RestController
@RequestMapping("/api/v1/requests")
public class ServiceRequestController {

    private final ServiceRequestService service;

    public ServiceRequestController(ServiceRequestService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ServiceRequestListResponse> getRequests(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        ServiceRequestListResponse response = service.getRequests(
                authentication.getName(),
                type,
                status,
                page,
                size
        );

        return ResponseEntity.ok(response);
    }
}
