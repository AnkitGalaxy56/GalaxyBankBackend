
package com.abcbank.mobile_banking.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NomineeRequest {

    @NotBlank
    private String accountNo;

    @NotBlank
    @Size(max = 150)
    private String nomineeName;

    @NotBlank
    @Size(max = 50)
    private String relationship;

    private LocalDate dateOfBirth;

    @Size(max = 15)
    private String mobileNo;

    private String address;

    @DecimalMin("0.01")
    @DecimalMax("100.00")
    private BigDecimal allocationPercentage;

    private Boolean isMinor = false;

    @Size(max = 150)
    private String guardianName;

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getNomineeName() {
        return nomineeName;
    }

    public void setNomineeName(String nomineeName) {
        this.nomineeName = nomineeName;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public void setMobileNo(String mobileNo) {
        this.mobileNo = mobileNo;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getAllocationPercentage() {
        return allocationPercentage;
    }

    public void setAllocationPercentage(BigDecimal allocationPercentage) {
        this.allocationPercentage = allocationPercentage;
    }

    public Boolean getIsMinor() {
        return isMinor;
    }

    public void setIsMinor(Boolean isMinor) {
        this.isMinor = isMinor;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName;
    }
}
