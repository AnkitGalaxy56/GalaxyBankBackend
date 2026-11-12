package com.abcbank.mobile_banking.dto.response;

import java.time.LocalDate;

public record ProfileResponse(
    String customerId,
    String title,
    String firstName,
    String middleName,
    String lastName,
    String fullName,
    String displayName,
    LocalDate dob,
    String mobile,
    String email,
    String kycStatus
) {}