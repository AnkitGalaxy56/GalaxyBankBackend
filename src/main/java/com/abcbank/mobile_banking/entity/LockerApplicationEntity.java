
package com.abcbank.mobile_banking.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "\"MB004009\"")
public class LockerApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_code", nullable = false, length = 20)
    private String branchCode;

    @Column(name = "customer_no", nullable = false, length = 30)
    private String customerNo;

    @Column(name = "account_no", nullable = false, length = 30)
    private String accountNo;

    @Column(name = "account_status", length = 20)
    private String accountStatus;

    @Column(name = "account_type", length = 20)
    private String accountType;

    @Column(name = "req_status", nullable = false, length = 20)
    private String reqStatus = "PENDING";

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "operation_yrs")
    private Short operationYears;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;


@Column(
    name = "created_on",
    nullable = false,
    insertable = false,
    updatable = false
)
private OffsetDateTime createdOn;

    @Column(name = "modified_by")
    private Long modifiedBy;

    @Column(name = "modified_on")
    private OffsetDateTime modifiedOn;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_on")
    private OffsetDateTime verifiedOn;

    // Getters and Setters

    public Long getId() {
        return id;
    }

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

    public String getReqStatus() {
        return reqStatus;
    }

    public void setReqStatus(String reqStatus) {
        this.reqStatus = reqStatus;
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

    public Short getOperationYears() {
        return operationYears;
    }

    public void setOperationYears(Short operationYears) {
        this.operationYears = operationYears;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedOn() {
        return createdOn;
    }

    public Long getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(Long modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public OffsetDateTime getModifiedOn() {
        return modifiedOn;
    }

    public void setModifiedOn(OffsetDateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }

    public Long getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Long verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public OffsetDateTime getVerifiedOn() {
        return verifiedOn;
    }

    public void setVerifiedOn(OffsetDateTime verifiedOn) {
        this.verifiedOn = verifiedOn;
    }
}
