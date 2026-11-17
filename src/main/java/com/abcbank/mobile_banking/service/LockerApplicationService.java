
package com.abcbank.mobile_banking.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.request.LockerApplicationRequest;
import com.abcbank.mobile_banking.repository.LockerApplicationRepository;

@Service
public class LockerApplicationService {

    private final LockerApplicationRepository repository;

    public LockerApplicationService(
            LockerApplicationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Map<String, Object> apply(
            String principal,
            LockerApplicationRequest request) {

        try {
            Map<String, Object> customer =
                    repository.customerByPrincipal(principal);

            Long userId =
                    ((Number) customer.get("user_id")).longValue();

            String customerNo =
                    String.valueOf(customer.get("customer_no"));

            Map<String, Object> account =
                    repository.activeSavingsAccount(
                            request.accountNo(),
                            customerNo);

            if (repository.hasPendingApplication(
                    customerNo, request.accountNo())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A pending locker application already exists for this account");
            }

            Long applicationId = repository.insertApplication(
                    String.valueOf(account.get("branch_code")),
                    customerNo,
                    String.valueOf(account.get("account_no")),
                    String.valueOf(account.get("account_status")),
                    String.valueOf(account.get("account_type")),
                    request.mobile(),
                    request.email(),
                    request.operationYears(),
                    request.lockerType(),
                    request.operationMode(),
                    request.nomineeName(),
                    userId);

            Map<String, Object> response = new LinkedHashMap<>();

            response.put(
                    "message",
                    "Locker application submitted successfully");

            response.put("applicationId", applicationId);
            response.put("customerNo", customerNo);
            response.put("accountNo", request.accountNo());
            response.put("operationYears", request.operationYears());
            response.put("lockerType", request.lockerType());
            response.put("operationMode", request.operationMode());
            response.put("nomineeName", request.nomineeName());
            response.put("status", "PENDING");

            return response;

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage());
        }
    }
}
