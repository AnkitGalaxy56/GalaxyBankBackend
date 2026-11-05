package com.abcbank.mobile_banking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.abcbank.mobile_banking.entity.MpinResetChallenge;

public interface MpinResetChallengeRepository extends JpaRepository<MpinResetChallenge, Long> {

    Optional<MpinResetChallenge> findTopByCustomerIdAndConsumedFalseOrderByCreatedAtDesc(String customerId);

    Optional<MpinResetChallenge> findTopByCustomerIdAndVerifiedTrueAndConsumedFalseOrderByCreatedAtDesc(String customerId);
}
