package com.abcbank.mobile_banking.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "registration_challenges", indexes = @Index(name = "idx_reg_challenge_customer", columnList = "customer_id"))
public class RegistrationChallenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "customer_id", nullable = false, length = 30) private String customerId;
    @Column(name = "otp_hash", nullable = false, length = 100) private String otpHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "attempts", nullable = false) private int attempts;
    @Column(name = "consumed", nullable = false) private boolean consumed;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected RegistrationChallenge() {}
    public RegistrationChallenge(String customerId, String otpHash, Instant expiresAt) { this.customerId=customerId; this.otpHash=otpHash; this.expiresAt=expiresAt; this.createdAt=Instant.now(); }
    public Long getId(){return id;} public String getCustomerId(){return customerId;} public String getOtpHash(){return otpHash;} public Instant getExpiresAt(){return expiresAt;} public int getAttempts(){return attempts;} public boolean isConsumed(){return consumed;}
    public void incrementAttempts(){attempts++;} public void consume(){consumed=true;}
}
