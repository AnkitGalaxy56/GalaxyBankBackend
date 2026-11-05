package com.abcbank.mobile_banking.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abcbank.mobile_banking.dto.request.ForgotMpinRequest;
import com.abcbank.mobile_banking.dto.request.RegistrationCompleteRequest;
import com.abcbank.mobile_banking.dto.request.VerifyMpinResetOtpRequest;
import com.abcbank.mobile_banking.dto.response.MessageResponse;
import com.abcbank.mobile_banking.dto.response.RegistrationStartResponse;
import com.abcbank.mobile_banking.dto.response.RegistrationTokenResponse;
import com.abcbank.mobile_banking.entity.CustomerRegistry;
import com.abcbank.mobile_banking.entity.MpinResetChallenge;
import com.abcbank.mobile_banking.repository.CustomerRegistryRepository;
import com.abcbank.mobile_banking.repository.MobileBankingUserRepository;
import com.abcbank.mobile_banking.repository.MpinResetChallengeRepository;
import com.abcbank.mobile_banking.security.JwtService;

/**
 * Forgot-MPIN flow
 *   1. requestOtp : 4-digit OTP sent to the registered mobile
 *   2. verifyOtp  : OTP checked -> short-lived "mpin:reset" token
 *   3. resetMpin  : new MPIN + confirm MPIN saved to MB001003
 */
@Service
public class MpinResetService {

    private static final int OTP_TTL_SECONDS = 300;
    private static final int RESET_WINDOW_SECONDS = 300;
    private static final int MAX_OTP_ATTEMPTS = 3;          // only 10,000 possible 4-digit OTPs
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private static final String SENT_MESSAGE =
            "If the details are eligible, an OTP has been sent to the registered mobile number.";
    private static final String INVALID_OTP = "OTP is invalid or expired.";

    private final CustomerRegistryRepository customers;
    private final MobileBankingUserRepository mobileUsers;
    private final MpinResetChallengeRepository challenges;
    private final PasswordEncoder encoder;
    private final OtpDeliveryService otpDelivery;
    private final JwtService jwt;
    private final JdbcTemplate jdbc;

    private final SecureRandom random = new SecureRandom();

    public MpinResetService(CustomerRegistryRepository customers,
                            MobileBankingUserRepository mobileUsers,
                            MpinResetChallengeRepository challenges,
                            PasswordEncoder encoder,
                            OtpDeliveryService otpDelivery,
                            JwtService jwt,
                            JdbcTemplate jdbc) {
        this.customers = customers;
        this.mobileUsers = mobileUsers;
        this.challenges = challenges;
        this.encoder = encoder;
        this.otpDelivery = otpDelivery;
        this.jwt = jwt;
        this.jdbc = jdbc;
    }

    // STEP 1: "Forgot MPIN" -> send 4-digit OTP
    @Transactional
    public RegistrationStartResponse requestOtp(ForgotMpinRequest request) {

        String customerId = request.customerId();

        Optional<CustomerRegistry> customer = customers.findById(customerId)
                .filter(CustomerRegistry::isEligibleForMobileBanking);

        // Same response for unknown / not-registered customers (no customer-ID probing).
        if (customer.isEmpty() || !mobileUsers.existsByCustomerId(customerId)) {
            return new RegistrationStartResponse(
                    SENT_MESSAGE,
                    "******" + String.format("%04d", random.nextInt(10_000)),
                    OTP_TTL_SECONDS);
        }

        String mobile = customer.get().getRegisteredMobile();

        Optional<MpinResetChallenge> latest =
                challenges.findTopByCustomerIdAndConsumedFalseOrderByCreatedAtDesc(customerId);

        // Resend cool-down: keep the OTP already sent, do not send another one yet.
        if (latest.isPresent()
                && !latest.get().isVerified()
                && latest.get().getExpiresAt().isAfter(Instant.now())
                && latest.get().getCreatedAt().isAfter(Instant.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            return new RegistrationStartResponse(SENT_MESSAGE, mask(mobile), OTP_TTL_SECONDS);
        }

        latest.ifPresent(MpinResetChallenge::consume);

        String otp = String.format("%04d", random.nextInt(10_000));

        challenges.save(new MpinResetChallenge(
                customerId,
                encoder.encode(otp),
                Instant.now().plusSeconds(OTP_TTL_SECONDS)));

        otpDelivery.sendOtp(mobile, otp);

        return new RegistrationStartResponse(SENT_MESSAGE, mask(mobile), OTP_TTL_SECONDS);
    }

    // STEP 2: "Submit OTP" -> short-lived reset token
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public RegistrationTokenResponse verifyOtp(VerifyMpinResetOtpRequest request) {

        String customerId = request.customerId();

        MpinResetChallenge challenge = challenges
                .findTopByCustomerIdAndConsumedFalseOrderByCreatedAtDesc(customerId)
                .filter(c -> !c.isVerified())
                .orElseThrow(() -> new IllegalArgumentException(INVALID_OTP));

        if (challenge.getAttempts() >= MAX_OTP_ATTEMPTS
                || challenge.getExpiresAt().isBefore(Instant.now())) {
            challenge.consume();
            throw new IllegalArgumentException(INVALID_OTP);
        }

        challenge.incrementAttempts();

        if (!encoder.matches(request.otp(), challenge.getOtpHash())) {
            if (challenge.getAttempts() >= MAX_OTP_ATTEMPTS) {
                challenge.consume();
            }
            throw new IllegalArgumentException(INVALID_OTP);
        }

        challenge.startResetWindow(Instant.now().plusSeconds(RESET_WINDOW_SECONDS));

        return new RegistrationTokenResponse(
                "OTP verified. Use this short-lived token only to set a new MPIN.",
                jwt.createMpinResetToken(customerId, RESET_WINDOW_SECONDS),
                "Bearer",
                RESET_WINDOW_SECONDS,
                customerId,
                "CU");
    }

    // STEP 3: "Enter new MPIN + Confirm MPIN" -> save
    @Transactional
    public MessageResponse resetMpin(String customerId, RegistrationCompleteRequest request) {

        if (!request.mpin().equals(request.confirmMpin())) {
            throw new IllegalArgumentException("MPIN and confirm MPIN do not match.");
        }

        // Proof (stored server-side) that the OTP step was passed and not used before.
        MpinResetChallenge challenge = challenges
                .findTopByCustomerIdAndVerifiedTrueAndConsumedFalseOrderByCreatedAtDesc(customerId)
                .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "MPIN reset session has expired. Please start again."));

        CustomerRegistry customer = customers.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer details could not be verified."));

        Long userId = customer.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("Customer is not linked to a User Master record.");
        }

        List<String> currentHash = jdbc.queryForList(
                "SELECT mpin_hash FROM public.\"MB001003\" WHERE user_id = ?", String.class, userId);

        if (currentHash.isEmpty()) {
            throw new IllegalArgumentException(
                    "MPIN is not set up for this customer. Please complete registration first.");
        }

        if (encoder.matches(request.mpin(), currentHash.get(0))) {
            throw new IllegalArgumentException("New MPIN must be different from your current MPIN.");
        }

        // Also clears any failed-attempt lock, so the customer can log in right away.
        jdbc.update("""
                UPDATE public."MB001003"
                SET mpin_hash = ?,
                    pin_changed_at = now(),
                    failed_attempts = 0,
                    locked_until = NULL,
                    status = CASE WHEN status = 'LOCKED' THEN 'ACTIVE' ELSE status END,
                    modified_by = ?,
                    modified_on = now()
                WHERE user_id = ?
                """, encoder.encode(request.mpin()), userId, userId);

        challenge.consume();

        return new MessageResponse("MPIN changed successfully. Please login with your new MPIN.");
    }

    private String mask(String mobile) {
        return "******" + mobile.substring(Math.max(0, mobile.length() - 4));
    }
}
