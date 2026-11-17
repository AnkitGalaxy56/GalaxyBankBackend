package com.abcbank.mobile_banking.service;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class KycCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final byte[] keyBytes;
    private final SecureRandom random = new SecureRandom();

    public KycCryptoService(
            @Value("${app.kyc.encryption-key:${KYC_ENCRYPTION_KEY:}}")
            String base64Key) {

        if (base64Key == null || base64Key.isBlank()) {
            this.keyBytes = null;
            return;
        }

        try {
            this.keyBytes = Base64.getDecoder().decode(base64Key.trim());
            if (this.keyBytes.length != 32) {
                throw new IllegalArgumentException(
                        "KYC encryption key must decode to exactly 32 bytes");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "KYC_ENCRYPTION_KEY must be a valid Base64-encoded 32-byte key", ex);
        }
    }

    public EncryptedFile encrypt(byte[] plainBytes) {
        if (keyBytes == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "KYC document encryption is not configured");
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, key,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            return new EncryptedFile(iv, cipher.doFinal(plainBytes));
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not securely process the KYC document");
        }
    }

    public record EncryptedFile(byte[] iv, byte[] content) {}
}
