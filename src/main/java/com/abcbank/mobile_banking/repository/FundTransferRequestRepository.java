package com.abcbank.mobile_banking.repository;

import com.abcbank.mobile_banking.entity.FundTransferRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundTransferRequestRepository extends JpaRepository<FundTransferRequestEntity, Long> {}
