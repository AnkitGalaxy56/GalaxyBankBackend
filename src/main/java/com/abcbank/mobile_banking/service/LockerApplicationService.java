
package com.abcbank.mobile_banking.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abcbank.mobile_banking.dto.request.LockerApplicationRequest;
import com.abcbank.mobile_banking.dto.response.LockerApplicationResponse;
import com.abcbank.mobile_banking.entity.LockerApplicationEntity;
import com.abcbank.mobile_banking.exception.LockerApplicationException;
import com.abcbank.mobile_banking.repository.LockerApplicationRepository;

@Service
public class LockerApplicationService {

    private final LockerApplicationRepository repository;

    public LockerApplicationService(
            LockerApplicationRepository repository) {
        this.repository = repository;
    }

    private Long getUserId(String customerNo) {
        return repository.findUserIdByCustomerNo(customerNo)
                .orElseThrow(() -> new LockerApplicationException(
                        "Customer user ID not found"));
    }

    private LockerApplicationResponse toResponse(
            LockerApplicationEntity entity, String message) {

        return new LockerApplicationResponse(
                true,
                message,
                entity.getId(),
                entity.getBranchCode(),
                entity.getCustomerNo(),
                entity.getAccountNo(),
                entity.getOperationYears() == null
                        ? null
                        : entity.getOperationYears().intValue(),
                entity.getReqStatus(),
                entity.getCreatedOn()
        );
    }

    @Transactional
    public LockerApplicationResponse applyLocker(
            LockerApplicationRequest request,
            String customerNo) {

        Long userId = getUserId(customerNo);

        if (request.getOperationYears() == null
                || request.getOperationYears() < 1
                || request.getOperationYears() > 25) {
            throw new LockerApplicationException(
                    "Operation period must be between 1 and 25 years");
        }

        boolean duplicate = repository
                .existsByCustomerNoAndAccountNoAndReqStatus(
                        customerNo,
                        request.getAccountNo(),
                        "PENDING"
                );

        if (duplicate) {
            throw new LockerApplicationException(
                    "A pending locker application already exists for this account");
        }

        LockerApplicationEntity entity =
                new LockerApplicationEntity();

        entity.setBranchCode(request.getBranchCode());
        entity.setCustomerNo(customerNo);
        entity.setAccountNo(request.getAccountNo());
        entity.setAccountStatus(request.getAccountStatus());
        entity.setAccountType(request.getAccountType());
        entity.setMobile(request.getMobile());
        entity.setEmail(request.getEmail());
        entity.setOperationYears(
                request.getOperationYears().shortValue());

        entity.setReqStatus("PENDING");
        entity.setCreatedBy(userId);

        LockerApplicationEntity saved =
                repository.saveAndFlush(entity);

        return toResponse(
                saved,
                "Locker application submitted successfully");
    }

    @Transactional(readOnly = true)
    public List<LockerApplicationResponse> getMyApplications(
            String customerNo) {

        Long userId = getUserId(customerNo);

        return repository
                .findByCreatedByOrderByCreatedOnDesc(userId)
                .stream()
                .map(entity -> toResponse(
                        entity,
                        "Locker application details"))
                .toList();
    }

    @Transactional(readOnly = true)
    public LockerApplicationResponse getApplication(
            Long applicationId,
            String customerNo) {

        Long userId = getUserId(customerNo);

        LockerApplicationEntity entity = repository
                .findByIdAndCreatedBy(applicationId, userId)
                .orElseThrow(() -> new LockerApplicationException(
                        "Locker application not found"));

        return toResponse(
                entity,
                "Locker application details");
    }

    @Transactional
    public LockerApplicationResponse cancelApplication(
            Long applicationId,
            String customerNo) {

        Long userId = getUserId(customerNo);

        LockerApplicationEntity entity = repository
                .findByIdAndCreatedBy(applicationId, userId)
                .orElseThrow(() -> new LockerApplicationException(
                        "Locker application not found"));

        if (!"PENDING".equalsIgnoreCase(entity.getReqStatus())) {
            throw new LockerApplicationException(
                    "Only pending applications can be cancelled");
        }

        entity.setReqStatus("CANCELLED");
        entity.setModifiedBy(userId);
        entity.setModifiedOn(OffsetDateTime.now());

        LockerApplicationEntity saved =
                repository.save(entity);

        return toResponse(
                saved,
                "Locker application cancelled successfully");
    }
}
