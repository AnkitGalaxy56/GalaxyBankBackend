
package com.abcbank.mobile_banking.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LockerApplicationRequest {

    @NotBlank(message = "Branch code is required")
    @Size(max = 20)
    private String branchCode;

    @NotBlank(message = "Customer number is required")
    @Size(max = 30)
    private String customerNo;

    @NotBlank(message = "Account number is required")
    @Size(max = 30)
    private String accountNo;

    @Size(max = 20)
    private String accountStatus;
    

    @Size(max = 20)
    private String accountType;

    @Size(max = 20)
    private String mobile;

    @Email(message = "Enter a valid email address")
    @Size(max = 254)
    private String email;

    @NotNull(message = "Operation period is required")
    @Min(value = 1, message = "Minimum period must be 1 year")
    @Max(value = 25, message = "Maximum period must be 25 years")
    private Integer operationYears;

    @NotNull(message = "Home branch consent is required")
    @AssertTrue(message = "Home branch consent is required")
    private Boolean homeBranchConsent;

    @NotNull(message = "Vacancy enquiry consent is required")
    @AssertTrue(message = "Vacancy enquiry consent is required")
    private Boolean vacancyEnquiryConsent;

    @NotNull(message = "Charges consent is required")
    @AssertTrue(message = "Charges consent is required")
    private Boolean chargesConsent;

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getOperationYears() {
        return operationYears;
    }

    public void setOperationYears(Integer operationYears) {
        this.operationYears = operationYears;
    }

    public Boolean getHomeBranchConsent() {
        return homeBranchConsent;
    }

    public void setHomeBranchConsent(Boolean homeBranchConsent) {
        this.homeBranchConsent = homeBranchConsent;
    }

    public Boolean getVacancyEnquiryConsent() {
        return vacancyEnquiryConsent;
    }

    public void setVacancyEnquiryConsent(Boolean vacancyEnquiryConsent) {
        this.vacancyEnquiryConsent = vacancyEnquiryConsent;
    }

    public Boolean getChargesConsent() {
        return chargesConsent;
    }

    public void setChargesConsent(Boolean chargesConsent) {
        this.chargesConsent = chargesConsent;
    }
}
