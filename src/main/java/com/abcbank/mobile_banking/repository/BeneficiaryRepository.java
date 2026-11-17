
package com.abcbank.mobile_banking.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.abcbank.mobile_banking.dto.response.BeneficiaryResponse;

@Repository
public class BeneficiaryRepository {

    private final JdbcTemplate jdbc;

    public BeneficiaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // JWT principal lookup
    public Map<String, Object> customerByPrincipal(String principal) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT user_id, customer_no
            FROM public."MB001001"
            WHERE status = 'ACTIVE'
              AND user_role = 'CUSTOMER'
              AND (
                    customer_no = ?
                    OR user_id::text = ?
              )
            """, principal, principal);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "Active customer was not found for this login");
        }

        return rows.get(0);
    }

    // Map actual database columns to existing response fields
    private final RowMapper<BeneficiaryResponse> beneficiaryRowMapper =
            (ResultSet rs, int rowNum) -> new BeneficiaryResponse(
                    rs.getLong("id"),
                    rs.getString("beneficiary_name"),
                    rs.getString("beneficiary_account"),
                    rs.getString("beneficiary_bank"),
                    rs.getString("beneficiary_ifsc"),
                    rs.getBigDecimal("single_transaction_limit"),
                    rs.getString("beneficiary_email"),
                    rs.getString("req_status"),
                    rs.getBoolean("is_verified"),
                    rs.getBoolean("is_active"),
                    rs.getObject("created_on", OffsetDateTime.class)
            );

    // Save beneficiary
    public Long saveBeneficiary(
            String branchCode,
            String customerNo,
            String beneficiaryName,
            String beneficiaryAccount,
            String beneficiaryBank,
            String beneficiaryIfsc,
            BigDecimal singleTransactionLimit,
            String beneficiaryEmail,
            Long createdBy) {

        return jdbc.queryForObject("""
            INSERT INTO public."MB004011" (
                branch_code,
                customer_no,
                beneficiary_name,
                beneficiary_account_no,
                confirm_account_no,
                beneficiary_bank_name,
                beneficiary_ifsc,
                beneficiary_email,
                transaction_limit,
                req_status,
                created_by,
                created_on
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?,
                'PENDING', ?, NOW()
            )
            RETURNING id
            """,
            Long.class,
            branchCode,
            customerNo,
            beneficiaryName,
            beneficiaryAccount,
            beneficiaryAccount,
            beneficiaryBank,
            beneficiaryIfsc,
            beneficiaryEmail,
            singleTransactionLimit,
            createdBy);
    }

    // Common SELECT with aliases matching BeneficiaryResponse
    private static final String BENEFICIARY_SELECT = """
        SELECT
            id,
            beneficiary_name,
            beneficiary_account_no AS beneficiary_account,
            beneficiary_bank_name AS beneficiary_bank,
            beneficiary_ifsc,
            transaction_limit AS single_transaction_limit,
            beneficiary_email,
            req_status,
            (req_status = 'APPROVED') AS is_verified,
            (req_status <> 'CANCELLED') AS is_active,
            created_on
        FROM public."MB004011"
        """;

    // Find beneficiary belonging to customer
    public Optional<BeneficiaryResponse> findByIdAndCustomer(
            Long beneficiaryId,
            String customerNo) {

        List<BeneficiaryResponse> results = jdbc.query(
                BENEFICIARY_SELECT + """
                    WHERE id = ?
                      AND customer_no = ?
                    """,
                beneficiaryRowMapper,
                beneficiaryId,
                customerNo);

        return results.stream().findFirst();
    }

    // List customer beneficiaries
    public List<BeneficiaryResponse> findAllByCustomer(String customerNo) {

        return jdbc.query(
                BENEFICIARY_SELECT + """
                    WHERE customer_no = ?
                      AND req_status <> 'CANCELLED'
                    ORDER BY created_on DESC
                    """,
                beneficiaryRowMapper,
                customerNo);
    }

    // Save OTP hash
    public void saveOtp(
            Long beneficiaryId,
            String customerNo,
            String otpHash,
            OffsetDateTime expiresAt) {

        jdbc.update("""
            INSERT INTO public."MB004012" (
                beneficiary_id,
                customer_no,
                otp_hash,
                expires_at,
                attempts,
                is_verified
            )
            VALUES (?, ?, ?, ?, 0, FALSE)
            """,
            beneficiaryId,
            customerNo,
            otpHash,
            expiresAt);
    }

    // Get latest OTP
    public Optional<OtpRecord> findLatestOtp(
            Long beneficiaryId,
            String customerNo) {

        List<OtpRecord> results = jdbc.query("""
            SELECT id, otp_hash, expires_at, attempts, is_verified
            FROM public."MB004012"
            WHERE beneficiary_id = ?
              AND customer_no = ?
            ORDER BY created_on DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new OtpRecord(
                    rs.getLong("id"),
                    rs.getString("otp_hash"),
                    rs.getObject("expires_at", OffsetDateTime.class),
                    rs.getInt("attempts"),
                    rs.getBoolean("is_verified")
            ),
            beneficiaryId,
            customerNo);

        return results.stream().findFirst();
    }

    // Increment OTP attempts
    public void incrementOtpAttempts(Long otpId) {

        jdbc.update("""
            UPDATE public."MB004012"
            SET attempts = attempts + 1
            WHERE id = ?
              AND attempts < 5
              AND is_verified = FALSE
            """,
            otpId);
    }

    // Mark OTP verified
    public void markOtpVerified(Long otpId) {

        jdbc.update("""
            UPDATE public."MB004012"
            SET is_verified = TRUE
            WHERE id = ?
              AND is_verified = FALSE
            """,
            otpId);
    }

    // Approve beneficiary after OTP verification
    public void activateBeneficiary(
            Long beneficiaryId,
            String customerNo) {

        jdbc.update("""
            UPDATE public."MB004011"
            SET req_status = 'APPROVED'
            WHERE id = ?
              AND customer_no = ?
              AND req_status = 'PENDING'
            """,
            beneficiaryId,
            customerNo);
    }

    // Deactivate beneficiary
    public void deactivateBeneficiary(
            Long beneficiaryId,
            String customerNo) {

        jdbc.update("""
            UPDATE public."MB004011"
            SET req_status = 'CANCELLED',
                modified_on = NOW()
            WHERE id = ?
              AND customer_no = ?
              AND req_status <> 'CANCELLED'
            """,
            beneficiaryId,
            customerNo);
    }

    public record OtpRecord(
            Long id,
            String otpHash,
            OffsetDateTime expiresAt,
            int attempts,
            boolean verified) {
    }
}
