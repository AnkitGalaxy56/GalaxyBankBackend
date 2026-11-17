package com.abcbank.mobile_banking.dto.response;

import java.time.OffsetDateTime;

public class LockerApplicationResponse {

    private boolean success;
    private String message;
    private Long applicationId;
    private String branchCode;
    private String customerNo;
    private String accountNo;
    private Integer operationYears;
    private String applicationStatus;
    private String requestStatus;
    private OffsetDateTime createdOn;

    public LockerApplicationResponse(
            boolean success,
            String message,
            Long applicationId,
            String branchCode,
            String customerNo,
            String accountNo,
            Integer operationYears,
            String applicationStatus,
            OffsetDateTime createdOn) {

        this.success = success;
        this.message = message;
        this.applicationId = applicationId;
        this.branchCode = branchCode;
        this.customerNo = customerNo;
        this.accountNo = accountNo;
        this.operationYears = operationYears;
        this.applicationStatus = applicationStatus;
        this.requestStatus = applicationStatus;
        this.createdOn = createdOn;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public Integer getOperationYears() {
        return operationYears;
    }

    public String getApplicationStatus() {
        return applicationStatus;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public OffsetDateTime getCreatedOn() {
        return createdOn;
    }
}