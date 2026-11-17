
package com.abcbank.mobile_banking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abcbank.mobile_banking.repository.BeneficiaryRepository;

@Service
public class BeneficiaryOtpService {

    private final BeneficiaryRepository repository;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.username}")
    private String senderEmail;

    public BeneficiaryOtpService(
            BeneficiaryRepository repository,
            JavaMailSender mailSender) {

        this.repository = repository;
        this.mailSender = mailSender;
    }

    // Generate OTP, save hash and send email
    @Transactional
    public void generateAndSendOtp(
            Long beneficiaryId,
            String customerNo,
            String beneficiaryEmail) {

        String otp = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        String otpHash = hashOtp(otp);

        OffsetDateTime expiresAt =
                OffsetDateTime.now().plusMinutes(5);

        repository.saveOtp(
                beneficiaryId,
                customerNo,
                otpHash,
                expiresAt
        );

        sendEmail(beneficiaryEmail, otp);
    }

    // Verify OTP
    @Transactional
    public boolean verifyOtp(
            Long beneficiaryId,
            String customerNo,
            String enteredOtp) {

        if (enteredOtp == null || !enteredOtp.matches("\\d{6}")) {
            return false;
        }

        var otpRecord = repository.findLatestOtp(
                beneficiaryId,
                customerNo
        );

        if (otpRecord.isEmpty()) {
            return false;
        }

        var record = otpRecord.get();

        // Already verified
        if (record.verified()) {
            return false;
        }

        // Maximum attempts reached
        if (record.attempts() >= record.maxAttempts()) {
            return false;
        }

        // OTP expired
        if (!OffsetDateTime.now().isBefore(record.expiresAt())) {
            return false;
        }

        // Count this verification attempt
        repository.incrementOtpAttempts(record.id());

        String enteredHash = hashOtp(enteredOtp);

        boolean matches = MessageDigest.isEqual(
                enteredHash.getBytes(StandardCharsets.UTF_8),
                record.otpHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            return false;
        }

        // Mark OTP verified
        repository.markOtpVerified(record.id());

        return true;
    }

    // SHA-256 OTP hashing
    private String hashOtp(String otp) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    otp.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Unable to hash OTP", ex
            );
        }
    }

    // Send OTP email
    private void sendEmail(
            String beneficiaryEmail,
            String otp) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(beneficiaryEmail);
        message.setSubject(
                "ABC Bank - Beneficiary Verification OTP"
        );

        message.setText(
                "Dear Customer,\n\n"
                + "Your ABC Bank beneficiary verification OTP is: "
                + otp
                + "\n\nThis OTP is valid for 5 minutes."
                + "\nDo not share this OTP with anyone."
                + "\n\nRegards,\nABC Bank"
        );

        mailSender.send(message);
    }
}
