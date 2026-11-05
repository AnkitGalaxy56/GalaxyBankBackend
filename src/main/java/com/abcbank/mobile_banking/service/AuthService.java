package com.abcbank.mobile_banking.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abcbank.mobile_banking.dto.request.MpinLoginRequest;
import com.abcbank.mobile_banking.dto.request.WebLoginRequest;
import com.abcbank.mobile_banking.dto.response.AuthResponse;
import com.abcbank.mobile_banking.exception.AuthException;
import com.abcbank.mobile_banking.security.JwtService;

/**
 * Web login  : username + password  -> MB001001 (user master) + MB001002 (password hash)
 * App login  : customerId + 4-digit MPIN -> mobile_banking_users + MB001003 (MPIN hash)
 */
@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long MPIN_LOCK_MINUTES = 30;

    private static final String INVALID_WEB = "Invalid username or password.";
    private static final String INVALID_MPIN = "Invalid customer ID or MPIN.";
    private static final String NOT_ACTIVE = "Your access is not active. Please contact the bank.";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    /** Used so an unknown user costs the same time as a known one (no user enumeration by timing). */
    private final String dummyHash;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwt) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("dummy-value-for-timing");
    }

    // ------------------------------------------------------------------
    // WEB LOGIN (username + password)
    // ------------------------------------------------------------------
    @Transactional(noRollbackFor = AuthException.class)
    public AuthResponse webLogin(WebLoginRequest request) {

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.user_id, u.user_type, u.customer_no, u.user_name, u.status,
                       u.bad_login, p.password_hash
                FROM public."MB001001" u
                JOIN public."MB001002" p ON p.user_id = u.user_id
                WHERE lower(u.user_name) = lower(?)
                """, request.username().trim());

        if (rows.isEmpty()) {
            encoder.matches(request.password(), dummyHash);
            throw new AuthException(HttpStatus.UNAUTHORIZED, INVALID_WEB);
        }

        Map<String, Object> u = rows.get(0);
        long userId = ((Number) u.get("user_id")).longValue();
        String status = String.valueOf(u.get("status"));
        int badLogin = ((Number) u.get("bad_login")).intValue();

        if (!encoder.matches(request.password(), String.valueOf(u.get("password_hash")))) {
            if ("ACTIVE".equals(status)) {
                int failed = badLogin + 1;
                String newStatus = failed >= MAX_FAILED_ATTEMPTS ? "LOCKED" : "ACTIVE";
                jdbc.update("""
                        UPDATE public."MB001001"
                        SET bad_login = ?, status = ?, modified_on = now()
                        WHERE user_id = ?
                        """, failed, newStatus, userId);
            }
            throw new AuthException(HttpStatus.UNAUTHORIZED, INVALID_WEB);
        }

        // Password is correct - now it is safe to tell the user about account state.
        if ("LOCKED".equals(status)) {
            throw new AuthException(HttpStatus.LOCKED,
                    "Your account is locked after too many failed attempts. Please contact the bank.");
        }
        if (!"ACTIVE".equals(status)) {
            throw new AuthException(HttpStatus.FORBIDDEN, NOT_ACTIVE);
        }

        jdbc.update("""
                UPDATE public."MB001001"
                SET bad_login = 0, login_time = now()
                WHERE user_id = ?
                """, userId);

        String userType = String.valueOf(u.get("user_type"));
        // Customers are identified by customer_no everywhere else (e.g. fund transfers).
        String subject = "CU".equals(userType) && u.get("customer_no") != null
                ? String.valueOf(u.get("customer_no"))
                : String.valueOf(u.get("user_name"));

        return new AuthResponse(
                "Login successful.",
                jwt.createAccessToken(subject, userType, "WEB"),
                "Bearer",
                subject);
    }

    // ------------------------------------------------------------------
    // APP LOGIN (customerId + 4-digit MPIN)
    // ------------------------------------------------------------------
    @Transactional(noRollbackFor = AuthException.class)
    public AuthResponse mpinLogin(MpinLoginRequest request) {

        String customerId = request.customerId().trim();

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.user_id,
                       m.registration_status,
                       u.status AS user_status,
                       c.mpin_hash,
                       c.status AS mpin_status,
                       c.failed_attempts,
                       (c.locked_until IS NOT NULL AND c.locked_until > now()) AS is_locked,
                       CEIL(EXTRACT(EPOCH FROM (c.locked_until - now())) / 60.0) AS lock_minutes_left
                FROM public.mobile_banking_users m
                JOIN public."MB001001" u ON u.user_id = m.user_id
                JOIN public."MB001003" c ON c.user_id = m.user_id
                WHERE m.customer_id = ?
                """, customerId);

        if (rows.isEmpty()) {
            encoder.matches(request.mpin(), dummyHash);
            throw new AuthException(HttpStatus.UNAUTHORIZED, INVALID_MPIN);
        }

        Map<String, Object> r = rows.get(0);
        long userId = ((Number) r.get("user_id")).longValue();

        if (Boolean.TRUE.equals(r.get("is_locked"))) {
            long mins = r.get("lock_minutes_left") == null ? MPIN_LOCK_MINUTES
                    : Math.max(1, ((Number) r.get("lock_minutes_left")).longValue());
            throw new AuthException(HttpStatus.LOCKED,
                    "MPIN is temporarily locked. Try again in " + mins
                            + " minute(s) or use Forgot MPIN.");
        }

        if (!encoder.matches(request.mpin(), String.valueOf(r.get("mpin_hash")))) {
            int failed = ((Number) r.get("failed_attempts")).intValue() + 1;
            if (failed >= MAX_FAILED_ATTEMPTS) {
                jdbc.update("""
                        UPDATE public."MB001003"
                        SET failed_attempts = 0, locked_until = ?, modified_on = now()
                        WHERE user_id = ?
                        """, Timestamp.from(Instant.now().plusSeconds(MPIN_LOCK_MINUTES * 60)), userId);
            } else {
                jdbc.update("""
                        UPDATE public."MB001003"
                        SET failed_attempts = ?, modified_on = now()
                        WHERE user_id = ?
                        """, failed, userId);
            }
            throw new AuthException(HttpStatus.UNAUTHORIZED, INVALID_MPIN);
        }

        if (!"ACTIVE".equals(String.valueOf(r.get("registration_status")))
                || !"ACTIVE".equals(String.valueOf(r.get("user_status")))
                || !"ACTIVE".equals(String.valueOf(r.get("mpin_status")))) {
            throw new AuthException(HttpStatus.FORBIDDEN, NOT_ACTIVE);
        }

        jdbc.update("""
                UPDATE public."MB001003"
                SET failed_attempts = 0, locked_until = NULL
                WHERE user_id = ?
                """, userId);
        jdbc.update("""
                UPDATE public."MB001001" SET login_time = now() WHERE user_id = ?
                """, userId);

        return new AuthResponse(
                "Login successful.",
                jwt.createAccessToken(customerId, "CU", "MOBILE"),
                "Bearer",
                customerId);
    }
}
