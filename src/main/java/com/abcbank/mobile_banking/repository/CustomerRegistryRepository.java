package com.abcbank.mobile_banking.repository;
import com.abcbank.mobile_banking.entity.CustomerRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerRegistryRepository extends JpaRepository<CustomerRegistry, String> {}
