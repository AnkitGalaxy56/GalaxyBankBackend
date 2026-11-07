
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
                        ? null : entity.getOperationYears().intValue(),
                entity.getReqStatus(),
                entity.getCreatedOn()
        );
    }

    // 1. Submit locker application

    @Transactional
    public LockerApplicationResponse applyLocker(
            LockerApplicationRequest request,
            Long userId) {

        if (request.getOperationYears() == null
                || request.getOperationYears() < 1
                || request.getOperationYears() > 25) {

            throw new LockerApplicationException(
                    "Operation period must be between 1 and 25 years");
        }

        boolean duplicate = repository
                .existsByCustomerNoAndAccountNoAndReqStatus(
                        request.getCustomerNo(),
                        request.getAccountNo(),
                        "PENDING");

        if (duplicate) {
            throw new LockerApplicationException(
                    "A pending locker application already exists for this account");
        }

        LockerApplicationEntity entity =
                new LockerApplicationEntity();

        entity.setBranchCode(request.getBranchCode());
        entity.setCustomerNo(request.getCustomerNo());
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

    // 2. View customer's applications

    @Transactional(readOnly = true)
    public List<LockerApplicationResponse> getMyApplications(
            Long userId) {

        return repository
                .findByCreatedByOrderByCreatedOnDesc(userId)
                .stream()
                .map(entity -> toResponse(
                        entity, "Locker application details"))
                .toList();
    }

    // 3. View application by ID

    @Transactional(readOnly = true)
    public LockerApplicationResponse getApplication(
            Long applicationId,
            Long userId) {

        LockerApplicationEntity entity = repository
                .findByIdAndCreatedBy(applicationId, userId)
                .orElseThrow(() ->
                        new LockerApplicationException(
                                "Locker application not found"));

        return toResponse(entity, "Locker application details");
    }

    // 4. Cancel pending application

    @Transactional
    public LockerApplicationResponse cancelApplication(
            Long applicationId,
            Long userId) {

        LockerApplicationEntity entity = repository
                .findByIdAndCreatedBy(applicationId, userId)
                .orElseThrow(() ->
                        new LockerApplicationException(
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
