
package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpiQrDecodeRequest(
    @NotBlank(message = "QR payload is required")
    @Size(max = 4096, message = "QR payload is too long")
    String qrPayload
) {}
