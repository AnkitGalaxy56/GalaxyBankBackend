package com.abcbank.mobile_banking.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a field that is null is left unchanged.
 * For optional fields (middleName) send "" to clear the value.
 * customerId, mobile, email and KYC status are NOT editable here.
 */
public record UpdateProfileRequest(

    @Pattern(regexp = "Mr\\.|Mrs\\.|Miss|Ms\\.|Dr\\.",
             message = "Title must be Mr., Mrs., Miss, Ms. or Dr.")
    String title,

    @Size(min = 1, max = 80)
    @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Invalid first name")
    String firstName,

    @Size(max = 80)
    @Pattern(regexp = "^$|^[\\p{L}][\\p{L} .'-]*$", message = "Invalid middle name")
    String middleName,

    @Size(max = 80)
    @Pattern(regexp = "^$|^[\\p{L}][\\p{L} .'-]*$", message = "Invalid last name")
    String lastName,

    @Size(min = 1, max = 100)
    String displayName,

    @Past(message = "Date of birth must be in the past")
    LocalDate dob
) {}
