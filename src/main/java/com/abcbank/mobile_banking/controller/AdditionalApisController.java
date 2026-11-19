package com.abcbank.mobile_banking.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.request.ChequeBookRequest;
import com.abcbank.mobile_banking.dto.request.CloseDepositRequest;
import com.abcbank.mobile_banking.dto.request.FundTransferRequest;
import com.abcbank.mobile_banking.dto.request.LockerApplicationRequest;
import com.abcbank.mobile_banking.dto.request.OpenFDRequest;
import com.abcbank.mobile_banking.dto.request.OpenRDRequest;
import com.abcbank.mobile_banking.dto.request.RegistrationStartRequest;
import com.abcbank.mobile_banking.dto.request.StopPaymentRequest;
import com.abcbank.mobile_banking.dto.request.VerifyBeneficiaryOtpRequest;
import com.abcbank.mobile_banking.dto.response.ChequeBookResponse;
import com.abcbank.mobile_banking.dto.response.DepositResponse;
import com.abcbank.mobile_banking.dto.response.FundTransferResponse;
import com.abcbank.mobile_banking.dto.response.StopPaymentResponse;
import com.abcbank.mobile_banking.repository.BeneficiaryRepository;
import com.abcbank.mobile_banking.service.BeneficiaryService;
import com.abcbank.mobile_banking.service.ChequeBookRequestService;
import com.abcbank.mobile_banking.service.DepositService;
import com.abcbank.mobile_banking.service.FundTransferService;
import com.abcbank.mobile_banking.service.LockerApplicationService;
import com.abcbank.mobile_banking.service.RegistrationService;
import com.abcbank.mobile_banking.service.StopPaymentService;

import jakarta.validation.Valid;

/**
 * Compatibility endpoints matching the API spreadsheet.
 * Existing controllers and their endpoint mappings are intentionally unchanged.
 */
@RestController
public class AdditionalApisController {

    private final RegistrationService registrationService;
    private final BeneficiaryService beneficiaryService;
    private final BeneficiaryRepository beneficiaryRepository;
    private final FundTransferService fundTransferService;
    private final DepositService depositService;
    private final ChequeBookRequestService chequeBookService;
    private final StopPaymentService stopPaymentService;
    private final LockerApplicationService lockerService;
    private final JdbcTemplate jdbc;

    public AdditionalApisController(
            RegistrationService registrationService,
            BeneficiaryService beneficiaryService,
            BeneficiaryRepository beneficiaryRepository,
            FundTransferService fundTransferService,
            DepositService depositService,
            ChequeBookRequestService chequeBookService,
            StopPaymentService stopPaymentService,
            LockerApplicationService lockerService,
            JdbcTemplate jdbc) {
        this.registrationService = registrationService;
        this.beneficiaryService = beneficiaryService;
        this.beneficiaryRepository = beneficiaryRepository;
        this.fundTransferService = fundTransferService;
        this.depositService = depositService;
        this.chequeBookService = chequeBookService;
        this.stopPaymentService = stopPaymentService;
        this.lockerService = lockerService;
        this.jdbc = jdbc;
    }

    private String principal(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login is required");
        }
        return authentication.getName();
    }

    // AUTH-03: resend by creating a fresh OTP and invalidating the prior challenge.
    // POST /api/v1/registration/resend-otp
    @PostMapping("/api/v1/registration/resend-otp")
    public ResponseEntity<?> resendRegistrationOtp(
            @Valid @RequestBody RegistrationStartRequest request) {
        return ResponseEntity.accepted().body(registrationService.start(request));
    }

    // TRF-03 spreadsheet-compatible alias. Existing /verify-otp remains available.
    // POST /api/v1/beneficiaries/{beneficiaryId}/verify-otp
    @PostMapping("/api/v1/beneficiaries/{beneficiaryId}/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyBeneficiaryOtpAlias(
            Authentication authentication,
            @PathVariable Long beneficiaryId,
            @Valid @RequestBody VerifyBeneficiaryOtpRequest request) {
        if (!beneficiaryId.equals(request.beneficiaryId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Path beneficiaryId and request beneficiaryId must match");
        }
        Map<String, Object> customer = beneficiaryRepository.customerByPrincipal(principal(authentication));
        String customerNo = String.valueOf(customer.get("customer_no"));
        String message = beneficiaryService.verifyBeneficiaryOtp(
                beneficiaryId, customerNo, request.otp());
        return ResponseEntity.ok(Map.of("message", message));
    }

    // TRF-08: spreadsheet-compatible transfer submission alias.
    // POST /api/v1/transfers
    @PostMapping("/api/v1/transfers")
    public ResponseEntity<FundTransferResponse> submitTransferAlias(
            Authentication authentication,
            @Valid @RequestBody FundTransferRequest request) {
        FundTransferResponse response = fundTransferService.submit(principal(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // TRF-11: transfer history for the authenticated customer only.
    // GET /api/v1/transfers
    @GetMapping("/api/v1/transfers")
    public ResponseEntity<List<Map<String, Object>>> transferHistory(Authentication authentication) {
        String customerNo = principal(authentication);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, customer_no, account_no, pay_mode,
                   ben_name, ben_account, ben_bank, ben_ifsc,
                   amount, remarks, req_status::text AS req_status,
                   is_sys, created_on, verified_on
            FROM public."MB004001"
            WHERE customer_no = ?
            ORDER BY created_on DESC, id DESC
            """, customerNo);
        return ResponseEntity.ok(rows);
    }

    // DEP-01 / DEP-07: combined FD and RD scheme list.
    // GET /api/v1/deposits/products and GET /api/v1/deposits/schemes
    @GetMapping({"/api/v1/deposits/products", "/api/v1/deposits/schemes"})
    public ResponseEntity<List<Map<String, Object>>> depositProducts(
            Authentication authentication) {
        String customerNo = principal(authentication);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : depositService.schemes("FD", customerNo)) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("depositType", "FD");
            result.add(item);
        }
        for (Map<String, Object> row : depositService.schemes("RD", customerNo)) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("depositType", "RD");
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // DEP-02: opening-request list (these tables store requests, not a separate core deposit master).
    // GET /api/v1/deposits
    @GetMapping("/api/v1/deposits")
    public ResponseEntity<List<Map<String, Object>>> depositRequestList(Authentication authentication) {
        String customerNo = principal(authentication);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id AS deposit_id, 'FD' AS deposit_type, customer_no,
                   account_no AS debit_account_no, req_status::text AS req_status,
                   prod_code, prod_name, scheme_code, scheme_name,
                   asof_date AS deposit_date, deposit_amount, int_rate,
                   tenure AS tenure_months, maturity_date, maturity_value,
                   auto_renewal, created_on
            FROM public."MB004004" WHERE customer_no = ?
            UNION ALL
            SELECT id AS deposit_id, 'RD' AS deposit_type, customer_no,
                   account_no AS debit_account_no, req_status::text AS req_status,
                   prod_code, prod_name, scheme_code, scheme_name,
                   asof_date AS deposit_date, deposit_amount, int_rate,
                   tenure AS tenure_months, maturity_date, maturity_value,
                   auto_renewal, created_on
            FROM public."MB004005" WHERE customer_no = ?
            ORDER BY created_on DESC, deposit_id DESC
            """, customerNo, customerNo);
        return ResponseEntity.ok(rows);
    }

    // DEP-03: details by opening-request ID. depositType=FD or RD is required
    // because FD and RD tables have independent sequences.
    // GET /api/v1/deposits/{depositId}?depositType=FD
    @GetMapping("/api/v1/deposits/{depositId}")
    public ResponseEntity<Map<String, Object>> depositRequestDetail(
            Authentication authentication,
            @PathVariable Long depositId,
            @RequestParam String depositType) {
        String customerNo = principal(authentication);
        String type = depositType.trim().toUpperCase();
        if (!type.equals("FD") && !type.equals("RD")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "depositType must be FD or RD");
        }
        String table = type.equals("FD") ? "MB004004" : "MB004005";
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id AS deposit_id, ? AS deposit_type, customer_no,
                   account_no AS debit_account_no, req_status::text AS req_status,
                   prod_code, prod_name, scheme_code, scheme_name,
                   cert_date, asof_date AS deposit_date, deposit_amount,
                   int_rate, offset_rate, tenure AS tenure_months,
                   auto_renewal, maturity_date, maturity_value,
                   cr_int_acid, maturity_acid, created_on,
                   verified_on
            FROM public.""" + table + """
            WHERE id = ? AND customer_no = ?
            """, type, depositId, customerNo);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deposit request not found");
        }
        return ResponseEntity.ok(rows.get(0));
    }

    // DEP-04 / DEP-05: aliases for FD/RD opening.
    @PostMapping("/api/v1/deposits/fd")
    public ResponseEntity<DepositResponse> openFdAlias(
            Authentication authentication, @Valid @RequestBody OpenFDRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(depositService.openFd(principal(authentication), request));
    }

    @PostMapping("/api/v1/deposits/rd")
    public ResponseEntity<DepositResponse> openRdAlias(
            Authentication authentication, @Valid @RequestBody OpenRDRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(depositService.openRd(principal(authentication), request));
    }

    // DEP-06: closure alias where the path ID is the FD/RD opening-request ID.
    // POST /api/v1/deposits/{depositId}/closure-requests
    @PostMapping("/api/v1/deposits/{depositId}/closure-requests")
    public ResponseEntity<Map<String, Object>> closeDepositAlias(
            Authentication authentication,
            @PathVariable Long depositId,
            @RequestBody Map<String, String> body) {
        CloseDepositRequest mapped = new CloseDepositRequest(
                body.get("depositType"), String.valueOf(depositId),
                body.get("proceedsMode"), body.get("proceedsAccountId"),
                body.get("beneficiaryName"), body.get("beneficiaryAccount"),
                body.get("beneficiaryBank"), body.get("beneficiaryIfsc"), body.get("remarks"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(depositService.close(principal(authentication), mapped));
    }

    // SRV-04: cheque-book endpoint alias.
    @PostMapping("/api/v1/cheque-books/requests")
    public ResponseEntity<ChequeBookResponse> chequeBookAlias(
            Authentication authentication, @Valid @RequestBody ChequeBookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chequeBookService.submit(principal(authentication), request));
    }

    // SRV-05: stop-payment endpoint alias.
    @PostMapping("/api/v1/cheques/stop-payments")
    public ResponseEntity<StopPaymentResponse> stopPaymentAlias(
            Authentication authentication, @Valid @RequestBody StopPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stopPaymentService.submit(principal(authentication), request));
    }

    // SRV-06: locker endpoint alias.
    @PostMapping("/api/v1/lockers/requests")
    public ResponseEntity<Map<String, Object>> lockerAlias(
            Authentication authentication, @Valid @RequestBody LockerApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lockerService.apply(principal(authentication), request));
    }
}
