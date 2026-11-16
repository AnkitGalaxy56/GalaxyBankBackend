
package com.abcbank.mobile_banking.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.abcbank.mobile_banking.dto.response.UpiQrDecodeResponse;

@Service
public class UpiQrService {

    private static final Pattern VPA_PATTERN =
        Pattern.compile("[A-Za-z0-9._-]{2,256}@[A-Za-z0-9.-]{2,64}");

    public UpiQrDecodeResponse decode(String payload) {

        try {
            URI uri = URI.create(payload.trim());

            if (!"upi".equalsIgnoreCase(uri.getScheme())
                    || !"pay".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException(
                    "Only UPI payment QR codes are accepted.");
            }

            Map<String, String> params = new HashMap<>();

            String query = uri.getRawQuery();

            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException(
                    "Invalid UPI QR payload.");
            }

            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);

                String key = URLDecoder.decode(
                    parts[0], StandardCharsets.UTF_8);

                String value = parts.length > 1
                    ? URLDecoder.decode(
                        parts[1], StandardCharsets.UTF_8)
                    : "";

                params.put(key, value);
            }

            String vpa = params.get("pa");
            String payeeName = params.get("pn");

            if (vpa == null || !VPA_PATTERN.matcher(vpa).matches()) {
                throw new IllegalArgumentException(
                    "Invalid UPI VPA in QR code.");
            }

            if (payeeName == null || payeeName.isBlank()) {
                throw new IllegalArgumentException(
                    "Payee name is missing from UPI QR.");
            }

            BigDecimal amount = null;
            String amountValue = params.get("am");

            if (amountValue != null && !amountValue.isBlank()) {
                try {
                    amount = new BigDecimal(amountValue);

                    if (amount.compareTo(BigDecimal.ZERO) <= 0
                            || amount.scale() > 2) {
                        throw new IllegalArgumentException(
                            "Invalid UPI QR amount.");
                    }
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(
                        "Invalid UPI QR amount.");
                }
            }

            return new UpiQrDecodeResponse(
                payeeName.trim(),
                vpa.trim(),
                amount
            );

        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                "Unable to decode UPI QR payload.");
        }
    }
}
