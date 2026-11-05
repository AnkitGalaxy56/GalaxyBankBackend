package com.abcbank.mobile_banking.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One "Forgot MPIN" OTP challenge. Table: public.mpin_reset_challenges */
@Entity
@Table(name = "mpin_reset_challenges", schema = "public")
public class MpinResetChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 30)
    private String customerId;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** true once the OTP was entered correctly (customer may now set a new MPIN). */
    @Column(name = "verified", nullable = false)
    private boolean verified;

    /** true once used up (new MPIN saved, too many attempts, expired, or replaced). */
    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MpinResetChallenge() {
    }

    public MpinResetChallenge(String customerId, String otpHash, Instant expiresAt) {
        this.customerId = customerId;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getOtpHash() { return otpHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public boolean isVerified() { return verified; }
    public boolean isConsumed() { return consumed; }
    public Instant getCreatedAt() { return createdAt; }

    public void incrementAttempts() { attempts++; }
    public void consume() { consumed = true; }

    /** OTP accepted: open a short window in which the new MPIN may be saved. */
    public void startResetWindow(Instant newExpiry) {
        this.verified = true;
        this.expiresAt = newExpiry;
    }
}
