package com.abcbank.mobile_banking.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.request.CloseDepositRequest;
import com.abcbank.mobile_banking.dto.request.OpenFDRequest;
import com.abcbank.mobile_banking.dto.request.OpenRDRequest;
import com.abcbank.mobile_banking.dto.response.DepositResponse;
import com.abcbank.mobile_banking.repository.DepositRepository;

@Service
public class DepositService {
    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Kolkata");
    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    private final DepositRepository repository;
    

    public DepositService(DepositRepository repository) { this.repository = repository; }

    

    private Long userId(String customerNo) {
        try { return repository.customerUserId(customerNo); }
        catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Active customer user was not found for this login");
        }
    }

    public List<Map<String,Object>> schemes(String type, String customerNo) {
        userId(customerNo);
        return repository.schemes(type);
    }

    @Transactional
    public DepositResponse openFd(String customerNo, OpenFDRequest req) {
        return open(customerNo, "FD", req.schemeCode(), req.principalAmount(),
                req.tenureMonths(), req.depositDate(), req.debitAccountId(),
                req.interestCreditAccountId(), req.maturityAccountId(), req.autoRenewal());
    }

    @Transactional
    public DepositResponse openRd(String customerNo, OpenRDRequest req) {
        return open(customerNo, "RD", req.schemeCode(), req.monthlyInstallment(),
                req.tenureMonths(), req.depositDate(), req.debitAccountId(),
                req.interestCreditAccountId(), req.maturityAccountId(), req.autoRenewal());
    }

    private DepositResponse open(String customerNo, String type, String schemeCode,
            BigDecimal amount, int tenure, LocalDate depositDate, String debitId,
            String interestId, String maturityId, boolean autoRenew) {
        Long maker = userId(customerNo);
        Map<String,Object> scheme = repository.scheme(schemeCode, type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Selected active " + type + " scheme was not found"));
        BigDecimal min = (BigDecimal) scheme.get("min_deposit");
        BigDecimal max = (BigDecimal) scheme.get("max_deposit");
        int minTenure = ((Number) scheme.get("min_tenure_months")).intValue();
        int maxTenure = ((Number) scheme.get("max_tenure_months")).intValue();
        if (amount.compareTo(min) < 0 || (max != null && amount.compareTo(max) > 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Deposit amount is outside the selected scheme limits");
        if (tenure < minTenure || tenure > maxTenure)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tenure is outside the selected scheme limits");
        if (depositDate.isBefore(LocalDate.now(BANK_ZONE)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Deposit date cannot be in the past");

        Map<String,Object> debit = savingsAccount(debitId, customerNo, "Debit");
        Map<String,Object> interest = savingsAccount(interestId, customerNo, "Interest credit");
        String maturityAccount = (maturityId == null || maturityId.isBlank()) ? debitId : maturityId;
        savingsAccount(maturityAccount, customerNo, "Maturity credit");

        BigDecimal rate = (BigDecimal) scheme.get("interest_rate");
        int frequency = ((Number) scheme.get("compounding_frequency")).intValue();
        LocalDate maturityDate = depositDate.plusMonths(tenure);
        BigDecimal maturityValue = "FD".equals(type)
                ? fdMaturity(amount, rate, frequency, tenure)
                : rdMaturity(amount, rate, maturityDate, depositDate);

        Long id = repository.insertOpening(
                "FD".equals(type) ? "\"MB004004\"" : "\"MB004005\"",
                String.valueOf(debit.get("branch_code")), customerNo, debitId,
                String.valueOf(debit.get("account_status")),
                String.valueOf(debit.get("account_type")),
                String.valueOf(scheme.get("prod_code")), String.valueOf(scheme.get("prod_name")),
                schemeCode, String.valueOf(scheme.get("scheme_name")), Date.valueOf(depositDate),
                amount, rate, tenure, autoRenew, interestId, maturityAccount,
                "FD".equals(type), maker, Date.valueOf(maturityDate), maturityValue);

        return new DepositResponse(type + " opening request submitted successfully",
                id, type, schemeCode, amount, rate, tenure, maturityDate,
                maturityValue, "PENDING");
    }

    private Map<String,Object> savingsAccount(String accountNo, String customerNo, String label) {
        Map<String,Object> account = repository.account(accountNo, customerNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        label + " account does not belong to the logged-in customer"));
        if (!"ACTIVE".equalsIgnoreCase(String.valueOf(account.get("account_status"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " account is not active");
        if (!"SAVINGS".equalsIgnoreCase(String.valueOf(account.get("account_type"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " account must be a Savings account");
        return account;
    }

    private BigDecimal fdMaturity(BigDecimal principal, BigDecimal rate, int frequency, int months) {
        double years = months / 12.0;
        double factor = Math.pow(1.0 + rate.doubleValue() / 100.0 / frequency,
                frequency * years);
        return principal.multiply(BigDecimal.valueOf(factor), MC).setScale(2, RoundingMode.HALF_UP);
    }

    // Monthly instalments are deposited at month-end; interest is credited quarterly.
    private BigDecimal rdMaturity(BigDecimal instalment, BigDecimal rate,
            LocalDate maturityDate, LocalDate startDate) {
        int months = (int) java.time.temporal.ChronoUnit.MONTHS.between(startDate, maturityDate);
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal quarterlyRate = rate.divide(BigDecimal.valueOf(400), MC);
        for (int month = 1; month <= months; month++) {
            balance = balance.add(instalment);
            if (month % 3 == 0) balance = balance.multiply(BigDecimal.ONE.add(quarterlyRate), MC);
        }
        return balance.setScale(2, RoundingMode.HALF_UP);
    }

@Transactional
public Map<String, Object> close(
        String customerNo,
        CloseDepositRequest req) {

    Long maker = userId(customerNo);

    String type = req.depositType().trim().toUpperCase();
    String mode = req.proceedsMode().trim().toUpperCase();

    if (!type.equals("FD") && !type.equals("RD")) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Deposit type must be FD or RD");
    }

    if (!mode.equals("SAME_BANK") && !mode.equals("OTHER_BANK")) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Proceeds mode must be SAME_BANK or OTHER_BANK");
    }

    // The depositAccountId must be the opening table's numeric ID.
    Long depositId;

    try {
        depositId = Long.parseLong(req.depositAccountId().trim());
        if (depositId <= 0) {
            throw new NumberFormatException();
        }
    } catch (NumberFormatException ex) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid depositAccountId. Send the FD/RD opening record ID.");
    }

    // Find only an approved FD/RD belonging to this customer.
    Map<String, Object> deposit =
            repository.depositAccount(depositId, customerNo, type)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Approved " + type +
                    " account was not found for this customer"));

    Map<String, Object> settlement;

    if ("SAME_BANK".equals(mode)) {

        if (blank(req.proceedsAccountId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select a Savings account for closure proceeds");
        }

        settlement = savingsAccount(
                req.proceedsAccountId(),
                customerNo,
                "Proceeds");

    } else {

        if (blank(req.beneficiaryName())
                || blank(req.beneficiaryAccount())
                || blank(req.beneficiaryBank())
                || blank(req.beneficiaryIfsc())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Beneficiary name, account, bank and IFSC are required for another bank");
        }

        if (!req.beneficiaryIfsc().matches(
                "^[A-Za-z]{4}0[A-Za-z0-9]{6}$")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid IFSC format");
        }

        // Use the Savings account associated with the approved
        // deposit opening record. Do not select an arbitrary account.
        settlement = deposit;
    }

    Long requestId = repository.insertClosure(
            String.valueOf(settlement.get("branch_code")),
            customerNo,
            String.valueOf(settlement.get("account_no")),
            String.valueOf(settlement.get("account_status")),
            String.valueOf(settlement.get("account_type")),
            String.valueOf(deposit.get("deposit_id")),
            type,
            mode,
            req.beneficiaryName(),
            req.beneficiaryAccount(),
            req.beneficiaryBank(),
            req.beneficiaryIfsc(),
            req.remarks(),
            maker
    );

    Map<String, Object> result = new LinkedHashMap<>();

    result.put("message",
            "Deposit closure request submitted successfully");
    result.put("requestId", requestId);
    result.put("depositType", type);
    result.put("depositAccountId", deposit.get("deposit_id"));
    result.put("proceedsMode", mode);
    result.put("status", "PENDING");

    return result;
}

   /*  @Transactional
    public Map<String,Object> close(String customerNo, CloseDepositRequest req) {
        Long maker = userId(customerNo);
        String type = req.depositType().trim().toUpperCase();
        String mode = req.proceedsMode().trim().toUpperCase();
        if (!type.equals("FD") && !type.equals("RD"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit type must be FD or RD");
        if (!mode.equals("SAME_BANK") && !mode.equals("OTHER_BANK"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proceeds mode must be SAME_BANK or OTHER_BANK");

        Map<String,Object> deposit = repository.depositAccount(req.depositAccountId(), customerNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "FD/RD account was not found for this customer"));
        if (!type.equalsIgnoreCase(String.valueOf(deposit.get("account_type"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selected deposit account does not match the selected deposit type");
        if (!"ACTIVE".equalsIgnoreCase(String.valueOf(deposit.get("account_status"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit account is not active");

        Map<String,Object> settlement;
        if (mode.equals("SAME_BANK")) {
            if (req.proceedsAccountId() == null || req.proceedsAccountId().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Select a Savings account for closure proceeds");
            settlement = savingsAccount(req.proceedsAccountId(), customerNo, "Proceeds");
        } else {
            if (blank(req.beneficiaryName()) || blank(req.beneficiaryAccount())
                    || blank(req.beneficiaryBank()) || blank(req.beneficiaryIfsc()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Beneficiary name, account, bank and IFSC are required for another bank");
            if (!req.beneficiaryIfsc().matches("^[A-Za-z]{4}0[A-Za-z0-9]{6}$"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IFSC format");
            settlement = repository.accountByTypeFallback(customerNo);
        }
        Long id = repository.insertClosure(String.valueOf(settlement.get("branch_code")),
                customerNo, String.valueOf(settlement.get("account_no")),
                String.valueOf(settlement.get("account_status")),
                String.valueOf(settlement.get("account_type")), req.depositAccountId(), type,
                mode, req.beneficiaryName(), req.beneficiaryAccount(), req.beneficiaryBank(),
                req.beneficiaryIfsc(), req.remarks(), maker);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("message", "Deposit closure request submitted successfully");
        result.put("requestId", id);
        result.put("depositType", type);
        result.put("depositAccountId", req.depositAccountId());
        result.put("proceedsMode", mode);
        result.put("status", "PENDING");
        return result;
    }*/

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
