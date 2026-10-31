package com.abcbank.mobile_banking.repository;
import com.abcbank.mobile_banking.entity.RegistrationChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RegistrationChallengeRepository extends JpaRepository<RegistrationChallenge, Long> {
    Optional<RegistrationChallenge> findTopByCustomerIdAndConsumedFalseOrderByCreatedAtDesc(String customerId);
}
