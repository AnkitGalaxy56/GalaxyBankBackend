package com.abcbank.mobile_banking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.abcbank.mobile_banking.dto.response.CustomerMeResponse;
import com.abcbank.mobile_banking.service.CustomerMeService;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerMeController {

    private final CustomerMeService customerMeService;

    public CustomerMeController(CustomerMeService customerMeService) {
        this.customerMeService = customerMeService;
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerMeResponse> getMyDetails(
            Authentication authentication
    ) {
        CustomerMeResponse response =
                customerMeService.getMyDetails(authentication.getName());

        return ResponseEntity.ok(response);
    }
}