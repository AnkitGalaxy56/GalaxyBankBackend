

package com.abcbank.mobile_banking.dto.response;

public record RegistrationCompleteResponse(
    String message,
    String customerId,
    String registrationStatus
) {}