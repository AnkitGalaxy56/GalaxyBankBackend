
package com.abcbank.mobile_banking.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "\"MB004001\"", schema = "public")
public class FundTransferRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_code", nullable = false)
    private String branchCode;

    @Column(name = "customer_no", nullable = false)
    private String customerNo;

    @Column(name = "account_no", nullable = false)
    private String accountNo;

    @Column(name = "account_status")
    private String accountStatus;

    @Column(name = "account_type")
    private String accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "req_status", nullable = false)
    private RequestStatus reqStatus = RequestStatus.PENDING;

    @Column(name = "ifsc_code")
    private String ifscCode;

    @Column(name = "pay_mode", nullable = false)
    private String payMode;

    @Column(name = "ben_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "ben_account", nullable = false)
    private String beneficiaryAccount;

    @Column(name = "ben_bank")
    private String beneficiaryBank;

    @Column(name = "ben_ifsc", nullable = false)
    private String beneficiaryIfsc;

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_on", nullable = false)
    private Instant createdOn = Instant.now();

    // true = Mobile App (1)
    // false = Control Panel (2)
    @Column(name = "is_sys")
    private Boolean isSys = true;

    public FundTransferRequestEntity(
            String branchCode,
            String customerNo,
            String accountNo,
            String accountStatus,
            String accountType,
            String payMode,
            String beneficiaryName,
            String beneficiaryAccount,
            String beneficiaryBank,
            String beneficiaryIfsc,
            BigDecimal amount,
            String remarks,
            Long createdBy) {

        this.branchCode = branchCode;
        this.customerNo = customerNo;
        this.accountNo = accountNo;
        this.accountStatus = accountStatus;
        this.accountType = accountType;
        this.payMode = payMode;
        this.beneficiaryName = beneficiaryName;
        this.beneficiaryAccount = beneficiaryAccount;
        this.beneficiaryBank = beneficiaryBank;
        this.beneficiaryIfsc = beneficiaryIfsc;
        this.amount = amount;
        this.remarks = remarks;
        this.createdBy = createdBy;

        // Mobile App
        this.isSys = true;
    }

    protected FundTransferRequestEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getReqStatus() {
        return reqStatus.name();
    }

    public void setReqStatus(RequestStatus reqStatus) {
        this.reqStatus = reqStatus;
    }

    public Instant getCreatedOn() {
        return createdOn;
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

    public String getAccountStatus() {
        return accountStatus;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public String getPayMode() {
        return payMode;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getBeneficiaryAccount() {
        return beneficiaryAccount;
    }

    public String getBeneficiaryBank() {
        return beneficiaryBank;
    }

    public String getBeneficiaryIfsc() {
        return beneficiaryIfsc;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getRemarks() {
        return remarks;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Boolean getIsSys() {
        return isSys;
    }

    public void setIsSys(Boolean isSys) {
        this.isSys = isSys;
    }
}
