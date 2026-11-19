
package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record ChequeBookRequest(

    @NotBlank
    @Size(max = 30)
    String accountNo,

    @NotNull
    @Min(1)
    @Max(32767)
    Integer noofBooks,

    @NotBlank
    @Pattern(
        regexp = "(?i)HOME|BRANCH",
        message = "Delivery must be HOME or BRANCH"
    )
    String delivery
    
) {}
