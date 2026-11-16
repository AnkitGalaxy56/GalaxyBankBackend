
package com.abcbank.mobile_banking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.abcbank.mobile_banking.entity.LockerApplicationEntity;

@Repository
public interface LockerApplicationRepository
        extends JpaRepository<LockerApplicationEntity, Long> {

    List<LockerApplicationEntity>
            findByCreatedByOrderByCreatedOnDesc(Long createdBy);

    Optional<LockerApplicationEntity>
            findByIdAndCreatedBy(Long id, Long createdBy);

    boolean existsByCustomerNoAndAccountNoAndReqStatus(
            String customerNo,
            String accountNo,
            String reqStatus);

    @Query(value = """
            SELECT user_id
            FROM public."MB001001"
            WHERE customer_no = :customerNo
              AND user_type = 'CU'
            """, nativeQuery = true)
    Optional<Long> findUserIdByCustomerNo(
            @Param("customerNo") String customerNo);
}
