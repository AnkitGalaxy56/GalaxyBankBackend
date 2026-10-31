package com.abcbank.mobile_banking.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mobile_banking_users")
public class MobileBankingUser {

    @Id
    @Column(name = "customer_id", nullable = false, length = 30)
    private String customerId;

    @Column(name = "mpin_hash", nullable = false, length = 100)
    private String mpinHash;

    @Column(name = "registration_status", nullable = false, length = 20)
    private String registrationStatus = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MobileBankingUser() {}

    public MobileBankingUser(String customerId, String mpinHash) {
        this.customerId = customerId;
        this.mpinHash = mpinHash;
        this.registrationStatus = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getMpinHash() {
        return mpinHash;
    }

    public String getRegistrationStatus() {
        return registrationStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}