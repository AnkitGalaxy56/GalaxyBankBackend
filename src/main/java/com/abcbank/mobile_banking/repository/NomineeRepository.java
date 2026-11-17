
package com.abcbank.mobile_banking.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NomineeRepository {

    private final JdbcTemplate jdbc;

    public NomineeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Find account belonging to authenticated customer
    public Map<String, Object> findAccount(
            String accountNo, String customerNo) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT branch_code, customer_no, account_no
            FROM "MB002003"
            WHERE account_no = ?
              AND customer_no = ?
            """, accountNo, customerNo);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                "Customer account not found");
        }

        return rows.get(0);
    }

    // Get customer user ID for audit fields
    public Long findCustomerUserId(String customerNo) {

        List<Long> users = jdbc.queryForList("""
            SELECT user_id
            FROM "MB001001"
            WHERE customer_no = ?
              AND user_type = 'CU'
            """, Long.class, customerNo);

        if (users.isEmpty()) {
            throw new IllegalArgumentException(
                "Customer user not found");
        }

        return users.get(0);
    }

    // Insert nominee
    public Long insert(
            Map<String, Object> account,
            String nomineeName,
            String relationship,
            Date dob,
            String mobile,
            String address,
            BigDecimal percentage,
            boolean minor,
            String guardian,
            Long createdBy) {

        return jdbc.queryForObject("""
            INSERT INTO "MB004014" (
                branch_code,
                customer_no,
                account_no,
                nominee_name,
                relationship,
                date_of_birth,
                mobile_no,
                address,
                allocation_percentage,
                is_minor,
                guardian_name,
                req_status,
                created_by,
                created_on
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    'PENDING', ?, CURRENT_TIMESTAMP)
            RETURNING id
            """,
            Long.class,
            account.get("branch_code"),
            account.get("customer_no"),
            account.get("account_no"),
            nomineeName,
            relationship,
            dob,
            mobile,
            address,
            percentage,
            minor,
            guardian,
            createdBy
        );
    }

    // List nominees for customer account
    public List<Map<String, Object>> findAll(
            String customerNo, String accountNo) {

        return jdbc.queryForList("""
            SELECT
                id,
                branch_code AS "branchCode",
                customer_no AS "customerNo",
                account_no AS "accountNo",
                nominee_name AS "nomineeName",
                relationship,
                date_of_birth AS "dateOfBirth",
                mobile_no AS "mobileNo",
                address,
                allocation_percentage AS "allocationPercentage",
                is_minor AS "isMinor",
                guardian_name AS "guardianName",
                req_status AS "reqStatus",
                created_on AS "createdOn",
                modified_on AS "modifiedOn"
            FROM "MB004014"
            WHERE customer_no = ?
              AND account_no = ?
            ORDER BY id DESC
            """, customerNo, accountNo);
    }

    // Find nominee by ID and authenticated customer
    public Optional<Map<String, Object>> findById(
            Long id, String customerNo) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT
                id,
                branch_code AS "branchCode",
                customer_no AS "customerNo",
                account_no AS "accountNo",
                nominee_name AS "nomineeName",
                relationship,
                date_of_birth AS "dateOfBirth",
                mobile_no AS "mobileNo",
                address,
                allocation_percentage AS "allocationPercentage",
                is_minor AS "isMinor",
                guardian_name AS "guardianName",
                req_status AS "reqStatus",
                created_on AS "createdOn",
                modified_on AS "modifiedOn"
            FROM "MB004014"
            WHERE id = ?
              AND customer_no = ?
            """, id, customerNo);

        return rows.stream().findFirst();
    }

    // Calculate existing allocation
    public BigDecimal totalAllocation(
            String customerNo,
            String accountNo,
            Long excludeId) {

        if (excludeId == null) {
            return jdbc.queryForObject("""
                SELECT COALESCE(
                    SUM(allocation_percentage), 0)
                FROM "MB004014"
                WHERE customer_no = ?
                  AND account_no = ?
                """,
                BigDecimal.class,
                customerNo,
                accountNo);
        }

        return jdbc.queryForObject("""
            SELECT COALESCE(
                SUM(allocation_percentage), 0)
            FROM "MB004014"
            WHERE customer_no = ?
              AND account_no = ?
              AND id <> ?
            """,
            BigDecimal.class,
            customerNo,
            accountNo,
            excludeId);
    }

    // Update nominee
    public int update(
            Long id,
            String customerNo,
            String nomineeName,
            String relationship,
            Date dob,
            String mobile,
            String address,
            BigDecimal percentage,
            boolean minor,
            String guardian,
            Long modifiedBy) {

        return jdbc.update("""
            UPDATE "MB004014"
            SET nominee_name = ?,
                relationship = ?,
                date_of_birth = ?,
                mobile_no = ?,
                address = ?,
                allocation_percentage = ?,
                is_minor = ?,
                guardian_name = ?,
                modified_by = ?,
                modified_on = CURRENT_TIMESTAMP
            WHERE id = ?
              AND customer_no = ?
            """,
            nomineeName,
            relationship,
            dob,
            mobile,
            address,
            percentage,
            minor,
            guardian,
            modifiedBy,
            id,
            customerNo);
    }

    // Delete nominee
    public int delete(Long id, String customerNo) {

        return jdbc.update("""
            DELETE FROM "MB004014"
            WHERE id = ?
              AND customer_no = ?
            """, id, customerNo);
    }
}
