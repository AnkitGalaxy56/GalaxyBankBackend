
package com.abcbank.mobile_banking.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.request.NomineeRequest;
import com.abcbank.mobile_banking.repository.NomineeRepository;

@Service
public class NomineeService {

    private final NomineeRepository repository;

    public NomineeService(NomineeRepository repository) {
        this.repository = repository;
    }

    private Long getUserId(String customerNo) {
        try {
            return repository.findCustomerUserId(customerNo);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Customer user not found"
            );
        }
    }

    private Map<String, Object> getAccount(
            String customerNo, String accountNo) {
        try {
            return repository.findAccount(accountNo, customerNo);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Customer account not found"
            );
        }
    }

    private BigDecimal getPercentage(NomineeRequest request) {

        BigDecimal percentage =
            request.getAllocationPercentage() == null
            ? new BigDecimal("100.00")
            : request.getAllocationPercentage();

        if (percentage.compareTo(BigDecimal.ZERO) <= 0 ||
            percentage.compareTo(new BigDecimal("100.00")) > 0) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Allocation must be between 0.01 and 100"
            );
        }

        return percentage;
    }

    private void validateMinor(NomineeRequest request) {

        if (Boolean.TRUE.equals(request.getIsMinor()) &&
            (request.getGuardianName() == null ||
             request.getGuardianName().isBlank())) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Guardian name is required for minor nominee"
            );
        }
    }

    private void validateAllocation(
            String customerNo,
            String accountNo,
            BigDecimal percentage,
            Long excludeId) {

        BigDecimal existing = repository.totalAllocation(
            customerNo, accountNo, excludeId);

        if (existing.add(percentage)
                .compareTo(new BigDecimal("100.00")) > 0) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Total nominee allocation cannot exceed 100%"
            );
        }
    }

    @Transactional
    public Map<String, Object> add(
            String customerNo, NomineeRequest request) {

        Map<String, Object> account =
            getAccount(customerNo, request.getAccountNo());

        BigDecimal percentage = getPercentage(request);

        validateAllocation(
            customerNo,
            request.getAccountNo(),
            percentage,
            null
        );

        validateMinor(request);

        Long userId = getUserId(customerNo);

        Long nomineeId = repository.insert(
            account,
            request.getNomineeName(),
            request.getRelationship(),
            request.getDateOfBirth() == null ? null :
                Date.valueOf(request.getDateOfBirth()),
            request.getMobileNo(),
            request.getAddress(),
            percentage,
            Boolean.TRUE.equals(request.getIsMinor()),
            request.getGuardianName(),
            userId
        );

        Map<String, Object> nominee =
            repository.findById(nomineeId, customerNo)
                .orElseThrow();

        return Map.of(
            "success", true,
            "message", "Nominee added successfully",
            "nominee", nominee
        );
    }

    public Map<String, Object> getAll(
            String customerNo, String accountNo) {

        getAccount(customerNo, accountNo);

        List<Map<String, Object>> nominees =
            repository.findAll(customerNo, accountNo);

        return Map.of(
            "success", true,
            "customerNo", customerNo,
            "accountNo", accountNo,
            "totalRecords", nominees.size(),
            "nominees", nominees
        );
    }

    public Map<String, Object> getOne(
            String customerNo, Long id) {

        return repository.findById(id, customerNo)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Nominee not found"
            ));
    }

    @Transactional
    public Map<String, Object> update(
            String customerNo,
            Long id,
            NomineeRequest request) {

        Map<String, Object> existing =
            repository.findById(id, customerNo)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Nominee not found"
                ));

        String existingAccount =
            String.valueOf(existing.get("accountNo"));

        if (!existingAccount.equals(request.getAccountNo())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Nominee account cannot be changed"
            );
        }

        BigDecimal percentage = getPercentage(request);

        validateAllocation(
            customerNo,
            existingAccount,
            percentage,
            id
        );

        validateMinor(request);

        Long userId = getUserId(customerNo);

        repository.update(
            id,
            customerNo,
            request.getNomineeName(),
            request.getRelationship(),
            request.getDateOfBirth() == null ? null :
                Date.valueOf(request.getDateOfBirth()),
            request.getMobileNo(),
            request.getAddress(),
            percentage,
            Boolean.TRUE.equals(request.getIsMinor()),
            request.getGuardianName(),
            userId
        );

        return Map.of(
            "success", true,
            "message", "Nominee updated successfully",
            "nominee", repository.findById(id, customerNo)
                .orElseThrow()
        );
    }

    @Transactional
    public Map<String, Object> delete(
            String customerNo, Long id) {

        int deleted = repository.delete(id, customerNo);

        if (deleted == 0) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Nominee not found"
            );
        }

        return Map.of(
            "success", true,
            "message", "Nominee deleted successfully"
        );
    }
}
