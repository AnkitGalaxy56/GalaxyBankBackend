
package com.abcbank.mobile_banking.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KycRepository {

    private final JdbcTemplate jdbc;

    public KycRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> findAccount(String customerNo, String accountNo) {

        List<Map<String, Object>> result = jdbc.queryForList(
                """
                SELECT branch_code, customer_no, account_no
                FROM "MB002003"
                WHERE customer_no = ?
                  AND account_no = ?
                """,
                customerNo, accountNo
        );

        if (result.isEmpty()) {
            throw new IllegalArgumentException("Account not found for this customer");
        }

        return result.get(0);
    }

    public Long findCustomerUserId(String customerNo) {

        List<Long> result = jdbc.query(
                """
                SELECT user_id
                FROM "MB001001"
                WHERE customer_no = ?
                  AND user_type = 'CU'
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getLong("user_id"),
                customerNo
        );

        if (result.isEmpty()) {
            throw new IllegalArgumentException("Customer user not found");
        }

        return result.get(0);
    }

    public Long saveDocument(
            String customerNo,
            String accountNo,
            String documentType,
            String documentName,
            String originalFileName,
            String storedFileName,
            String filePath,
            String extension,
            long fileSize,
            Long createdBy) {

        return jdbc.queryForObject(
                """
                INSERT INTO "MB004015"
                (
                    customer_no,
                    account_no,
                    document_type,
                    document_name,
                    original_file_name,
                    stored_file_name,
                    file_path,
                    file_extension,
                    file_size,
                    upload_status,
                    created_by,
                    created_on
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'UPLOADED', ?, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                customerNo,
                accountNo,
                documentType,
                documentName,
                originalFileName,
                storedFileName,
                filePath,
                extension,
                fileSize,
                createdBy
        );
    }

    public List<Map<String, Object>> findDocuments(
            String customerNo,
            String accountNo) {

        return jdbc.queryForList(
                """
                SELECT
                    id,
                    document_type,
                    document_name,
                    original_file_name,
                    file_extension,
                    file_size,
                    upload_status,
                    created_on
                FROM "MB004015"
                WHERE customer_no = ?
                  AND account_no = ?
                ORDER BY created_on DESC
                """,
                customerNo, accountNo
        );
    }

    public int countRequiredDocuments(String customerNo, String accountNo) {

        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(DISTINCT document_type)
                FROM "MB004015"
                WHERE customer_no = ?
                  AND account_no = ?
                  AND upload_status = 'UPLOADED'
                  AND document_type IN
                    ('AADHAAR_FRONT', 'AADHAAR_BACK', 'ADDRESS_PROOF', 'SELFIE')
                """,
                Integer.class,
                customerNo,
                accountNo
        );

        return count == null ? 0 : count;
    }

    public void ensureProgress(String customerNo, String accountNo) {

        jdbc.update(
                """
                INSERT INTO "MB004016"
                (
                    customer_no,
                    account_no,
                    current_step,
                    kyc_status,
                    created_on
                )
                VALUES (?, ?, 1, 'IN_PROGRESS', CURRENT_TIMESTAMP)
                ON CONFLICT (customer_no, account_no) DO NOTHING
                """,
                customerNo,
                accountNo
        );
    }

    public void updateUploadProgress(
            String customerNo,
            String accountNo,
            boolean completed) {

        jdbc.update(
                """
                UPDATE "MB004016"
                SET upload_documents = ?,
                    current_step = CASE
                        WHEN ? = TRUE THEN GREATEST(current_step, 2)
                        ELSE current_step
                    END,
                    modified_on = CURRENT_TIMESTAMP
                WHERE customer_no = ?
                  AND account_no = ?
                """,
                completed,
                completed,
                customerNo,
                accountNo
        );
    }

    public Map<String, Object> findProgress(
            String customerNo,
            String accountNo) {

        List<Map<String, Object>> result = jdbc.queryForList(
                """
                SELECT
                    customer_no,
                    account_no,
                    current_step,
                    upload_documents,
                    verify_details,
                    review_completed,
                    final_approved,
                    kyc_status,
                    rejection_reason,
                    submitted_on,
                    verified_by,
                    verified_on,
                    created_on,
                    modified_on
                FROM "MB004016"
                WHERE customer_no = ?
                  AND account_no = ?
                """,
                customerNo,
                accountNo
        );

        if (result.isEmpty()) {
            throw new IllegalStateException("KYC progress not found");
        }

        return result.get(0);
    }

    public int verifyDetails(String customerNo, String accountNo) {

        return jdbc.update(
                """
                UPDATE "MB004016"
                SET verify_details = TRUE,
                    current_step = GREATEST(current_step, 3),
                    modified_on = CURRENT_TIMESTAMP
                WHERE customer_no = ?
                  AND account_no = ?
                  AND upload_documents = TRUE
                  AND final_approved = FALSE
                  AND kyc_status = 'IN_PROGRESS'
                """,
                customerNo,
                accountNo
        );
    }

    public int submitKyc(String customerNo, String accountNo) {

        return jdbc.update(
                """
                UPDATE "MB004016"
                SET review_completed = TRUE,
                    kyc_status = 'PENDING_REVIEW',
                    submitted_on = CURRENT_TIMESTAMP,
                    modified_on = CURRENT_TIMESTAMP
                WHERE customer_no = ?
                  AND account_no = ?
                  AND upload_documents = TRUE
                  AND verify_details = TRUE
                  AND final_approved = FALSE
                  AND kyc_status = 'IN_PROGRESS'
                """,
                customerNo,
                accountNo
        );
    }
}
