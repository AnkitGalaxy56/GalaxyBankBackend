
package com.abcbank.mobile_banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mb001001cr", schema = "public")
public class CustomerRegistry {

    @Id
    @Column(name = "customer_id", nullable = false, length = 30)
    private String customerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "registered_mobile", nullable = false, length = 20)
    private String registeredMobile;

    @Column(name = "eligible_for_mobile_banking", nullable = false)
    private boolean eligibleForMobileBanking = true;

    protected CustomerRegistry() {
    }

    public String getCustomerId() {
        return customerId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRegisteredMobile() {
        return registeredMobile;
    }

    public boolean isEligibleForMobileBanking() {
        return eligibleForMobileBanking;
    }
}
