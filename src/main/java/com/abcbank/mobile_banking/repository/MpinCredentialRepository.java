
package com.abcbank.mobile_banking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abcbank.mobile_banking.entity.MpinCredential;

public interface MpinCredentialRepository
        extends JpaRepository<MpinCredential, Long> {

    Optional<MpinCredential> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByCustomerId(String customerId);

    
}
