package com.abcbank.mobile_banking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abcbank.mobile_banking.entity.FundTransferRequestEntity;

public interface FundTransferRequestRepository
        extends JpaRepository<FundTransferRequestEntity, Long> {

    Optional<FundTransferRequestEntity> findByIdAndCustomerNo(
            Long id,
            String customerNo
    );
}