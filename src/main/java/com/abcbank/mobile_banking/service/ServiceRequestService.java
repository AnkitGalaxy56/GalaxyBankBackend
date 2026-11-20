
package com.abcbank.mobile_banking.service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.dto.response.ServiceRequestItemResponse;
import com.abcbank.mobile_banking.dto.response.ServiceRequestListResponse;

@Service
public class ServiceRequestService {

    private final JdbcTemplate jdbc;

    public ServiceRequestService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "REMITTANCE",
            "CHEQUE_BOOK",
            "STOP_PAYMENT",
            "FD_OPENING",
            "RD_OPENING",
            "EMI_PAYMENT",
            "FD_CLOSING",
            "STANDING_INSTRUCTION",
            "LOCKER",
            "LOAN_APPLICATION"
    );

    private static final String UNION_SQL = """
        SELECT
            'REMITTANCE' AS request_type,
            'REMITTANCE-' || id AS request_id,
            customer_no,
            req_status AS status,
            CONCAT(pay_mode, ' transfer to ', ben_name) AS summary,
            amount,
            created_on
        FROM public."MB004001"

        UNION ALL

        SELECT
            'CHEQUE_BOOK',
            'CHEQUE_BOOK-' || id,
            customer_no,
            req_status,
           CONCAT('Cheque book - ', COALESCE(noof_leaves::text, '25'), ' leaves'),
            NULL::numeric,
            created_on
        FROM public."MB004002"

        UNION ALL

        SELECT
            'STOP_PAYMENT',
            'STOP_PAYMENT-' || id,
            customer_no,
            req_status,
            CONCAT('Stop payment - cheque ', cheque_no),
            cheque_amount,
            created_on
        FROM public."MB004003"

        UNION ALL

        SELECT
            'FD_OPENING',
            'FD_OPENING-' || id,
            customer_no,
            req_status,
            CONCAT('FD opening - ', scheme_name),
            deposit_amount,
            created_on
        FROM public."MB004004"

        UNION ALL

        SELECT
            'RD_OPENING',
            'RD_OPENING-' || id,
            customer_no,
            req_status,
            CONCAT('RD opening - ', scheme_name),
            deposit_amount,
            created_on
        FROM public."MB004005"

        UNION ALL

        SELECT
            'EMI_PAYMENT',
            'EMI_PAYMENT-' || id,
            customer_no,
            req_status,
            CONCAT('EMI payment - ', loan_ac_name),
            emi_amount,
            created_on
        FROM public."MB004006"

        UNION ALL

        SELECT
            'FD_CLOSING',
            'FD_CLOSING-' || id,
            customer_no,
            req_status,
            CONCAT('FD closing - ', fd_acid),
            amount,
            created_on
        FROM public."MB004007"

        UNION ALL

        SELECT
            'STANDING_INSTRUCTION',
            'STANDING_INSTRUCTION-' || id,
            customer_no,
            req_status,
            CONCAT('Standing instruction - ', frequency),
            debit_amount,
            created_on
        FROM public."MB004008"

        UNION ALL

        SELECT
            'LOCKER',
            'LOCKER-' || id,
            customer_no,
            req_status,
            CONCAT('Locker application - ', locker_type),
            NULL::numeric,
            created_on
        FROM public."MB004009"

        UNION ALL

        SELECT
            'LOAN_APPLICATION',
            'LOAN_APPLICATION-' || id,
            customer_no,
            req_status,
            CONCAT('Loan application - ', product_code),
            loan_amount,
            created_on
        FROM public."MB004010"
        """;

   


        private static final String FILTER_SQL = """
    WHERE customer_no = ?
      AND (CAST(? AS VARCHAR) IS NULL OR request_type = ?)
      AND (CAST(? AS VARCHAR) IS NULL OR UPPER(status) = ?)
    """;

    public ServiceRequestListResponse getRequests(
            String customerId,
            String type,
            String status,
            int page,
            int size) {

        if (customerId == null || customerId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid customer authentication."
            );
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be 0 or greater and size must be between 1 and 100."
            );
        }

        String requestType = normalizeType(type);
        String requestStatus = normalizeStatus(status);

        if (requestType != null && !ALLOWED_TYPES.contains(requestType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid request type."
            );
        }

        long offset = (long) page * size;

        String countSql = """
            SELECT COUNT(*)
            FROM (
            """ + UNION_SQL + """
            ) requests
            """ + FILTER_SQL;

        Long totalElements = jdbc.queryForObject(
                countSql,
                Long.class,
                customerId,
                requestType, requestType,
                requestStatus, requestStatus
        );

        String listSql = """
            SELECT
                request_id,
                request_type,
                status,
                summary,
                amount,
                created_on
            FROM (
            """ + UNION_SQL + """
            ) requests
            """ + FILTER_SQL + """
            ORDER BY created_on DESC NULLS LAST, request_id DESC
            LIMIT ? OFFSET ?
            """;

        List<ServiceRequestItemResponse> items = jdbc.query(
                listSql,
                (rs, rowNum) -> {
                    Timestamp timestamp = rs.getTimestamp("created_on");

                    return new ServiceRequestItemResponse(
                            rs.getString("request_id"),
                            rs.getString("request_type"),
                            rs.getString("status"),
                            rs.getString("summary"),
                            rs.getBigDecimal("amount"),
                            timestamp == null ? null : timestamp.toInstant()
                    );
                },
                customerId,
                requestType, requestType,
                requestStatus, requestStatus,
                size,
                offset
        );

        long total = totalElements == null ? 0 : totalElements;
        int totalPages = (int) Math.ceil((double) total / size);

        return new ServiceRequestListResponse(
                page,
                size,
                total,
                totalPages,
                items
        );
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        return type.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        return status.trim().toUpperCase(Locale.ROOT);
    }
}
