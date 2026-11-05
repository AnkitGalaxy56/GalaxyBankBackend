
package com.abcbank.mobile_banking.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "\"MB001003\"", schema = "public")
public class MpinCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mpin_id")
    private Long mpinId;

    @Column(name = "customer_id", nullable = false, unique = true, length = 30)
    private String customerId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "mpin_hash", nullable = false, length = 255)
    private String mpinHash;

    @Column(name = "pin_changed_at", nullable = false)
    private Instant pinChangedAt;

    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_on", nullable = false)
    private Instant createdOn;

    @Column(name = "modified_by")
    private Long modifiedBy;

    @Column(name = "modified_on")
    private Instant modifiedOn;

    protected MpinCredential() {
    }

    public MpinCredential(
            String customerId,
            Long userId,
            String mpinHash) {

        this.customerId = customerId;
        this.userId = userId;
        this.mpinHash = mpinHash;
        this.pinChangedAt = Instant.now();
        this.failedAttempts = 0;
        this.status = "ACTIVE";
        this.createdOn = Instant.now();
    }

    public Long getMpinId() {
        return mpinId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getMpinHash() {
        return mpinHash;
    }

    public Instant getPinChangedAt() {
        return pinChangedAt;
    }

    public Integer getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public String getStatus() {
        return status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public Long getModifiedBy() {
        return modifiedBy;
    }

    public Instant getModifiedOn() {
        return modifiedOn;
    }
}
