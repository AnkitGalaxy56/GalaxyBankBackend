
package com.abcbank.mobile_banking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abcbank.mobile_banking.entity.MobileBankingUser;


public interface MobileBankingUserRepository
        extends JpaRepository<MobileBankingUser, String> {
            

    boolean existsByCustomerId(String customerId);

    boolean existsByUserId(Long userId);
}
