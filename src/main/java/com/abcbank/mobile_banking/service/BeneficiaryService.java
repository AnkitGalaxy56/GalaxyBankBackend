
package com.abcbank.mobile_banking.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.request.BeneficiaryRequest;
import com.abcbank.mobile_banking.dto.response.BeneficiaryResponse;
import com.abcbank.mobile_banking.repository.BeneficiaryRepository;


@Service
public class BeneficiaryService {

    private static final BigDecimal MAX_TRANSACTION_LIMIT =
            new BigDecimal("50000.00");

    private final BeneficiaryRepository repository;
    private final BeneficiaryOtpService otpService;

    public BeneficiaryService(
            BeneficiaryRepository repository,
            BeneficiaryOtpService otpService) {
        this.repository = repository;
        this.otpService = otpService;
    }

    @Transactional
    public String addBeneficiary(
            BeneficiaryRequest request,
            String customerNo,
            Long userId) {

        if (request.beneficiaryEmail() == null
                || request.beneficiaryEmail().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Beneficiary email is required for OTP verification");
        }

        if (request.singleTransactionLimit()
                .compareTo(MAX_TRANSACTION_LIMIT) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum single transaction limit is Rs. 50,000");
        }

        Long beneficiaryId = repository.saveBeneficiary(
                request.branchCode(),
                customerNo,
                request.beneficiaryName(),
                request.beneficiaryAccount(),
                request.beneficiaryBank(),
                request.beneficiaryIfsc().toUpperCase(),
                request.singleTransactionLimit(),
                request.beneficiaryEmail(),
                userId);

        otpService.generateAndSendOtp(
                beneficiaryId, customerNo, request.beneficiaryEmail());

        return "Beneficiary submitted successfully. OTP sent to beneficiary email.";
    }

    @Transactional
    public String verifyBeneficiaryOtp(
            Long beneficiaryId,
            String customerNo,
            String otp) {

        BeneficiaryResponse beneficiary = repository
                .findByIdAndCustomer(beneficiaryId, customerNo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Beneficiary not found"));

        if (beneficiary.verified()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Beneficiary is already verified");
        }

        if (!repository.isPendingBeneficiary(beneficiaryId, customerNo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only pending beneficiaries can be verified");
        }

        boolean verified = otpService.verifyOtp(
                beneficiaryId, customerNo, otp);

        if (!verified) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid or expired OTP. Maximum 5 attempts allowed.");
        }

        repository.activateBeneficiary(beneficiaryId, customerNo);

        return "Beneficiary verified successfully.";
    }

    public List<BeneficiaryResponse> getBeneficiaries(String customerNo) {
        return repository.findAllByCustomer(customerNo);
    }

    @Transactional
    public String updateBeneficiary(
            Long beneficiaryId,
            String customerNo,
            String beneficiaryName,
            BigDecimal singleTransactionLimit) {

        BeneficiaryResponse beneficiary = repository
                .findByIdAndCustomer(beneficiaryId, customerNo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Beneficiary not found"));

        if (!beneficiary.active() || !beneficiary.verified()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only active, verified beneficiaries can be updated");
        }

        if (singleTransactionLimit.compareTo(BigDecimal.ZERO) <= 0
                || singleTransactionLimit.compareTo(MAX_TRANSACTION_LIMIT) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Transaction limit must be between Rs. 0.01 and Rs. 50,000");
        }

        int updated = repository.updateBeneficiaryDetails(
                beneficiaryId,
                customerNo,
                beneficiaryName.trim(),
                singleTransactionLimit);

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Beneficiary could not be updated");
        }

        return "Beneficiary updated successfully.";
    }

    @Transactional
    public String deactivateBeneficiary(
            Long beneficiaryId,
            String customerNo) {

        BeneficiaryResponse beneficiary = repository
                .findByIdAndCustomer(beneficiaryId, customerNo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Beneficiary not found"));

        if (!beneficiary.active()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Beneficiary is already inactive");
        }

        repository.deactivateBeneficiary(beneficiaryId, customerNo);

        return "Beneficiary deleted successfully.";
    }
}
