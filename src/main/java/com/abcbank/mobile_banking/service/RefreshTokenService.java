package com.abcbank.mobile_banking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.abcbank.mobile_banking.dto.response.RefreshTokenResponse;
import com.abcbank.mobile_banking.exception.AuthException;
import com.abcbank.mobile_banking.security.JwtService;

@Service
public class RefreshTokenService {
    private static final long IDLE_TTL_SECONDS = 900; // 15 minutes
    private final JdbcTemplate jdbc;
    private final JwtService jwt;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(JdbcTemplate jdbc, JwtService jwt) {
        this.jdbc = jdbc;
        this.jwt = jwt;
    }

    @Transactional
    public RefreshTokenResponse createInitialToken(long userId, String customerId, String deviceId) {
        UUID sessionId = UUID.randomUUID();
        String token = newOpaqueToken();
        jdbc.update("""
            INSERT INTO public.mobile_auth_sessions
              (session_id, user_id, customer_id, device_id, expires_at)
            VALUES (?, ?, ?, ?, now() + (? * interval '1 second'))
            """, sessionId, userId, customerId, deviceId, IDLE_TTL_SECONDS);
        jdbc.update("""
            INSERT INTO public.mobile_auth_refresh_tokens
              (session_id, user_id, customer_id, device_id, token_hash, expires_at)
            VALUES (?, ?, ?, ?, ?, now() + (? * interval '1 second'))
            """, sessionId, userId, customerId, deviceId, hash(token), IDLE_TTL_SECONDS);
        return new RefreshTokenResponse(jwt.createAccessToken(customerId, "CU", "MOBILE", sessionId.toString()),
                token, "Bearer", jwt.getAccessTtlSeconds());
    }

    @Transactional(noRollbackFor = AuthException.class)
    public RefreshTokenResponse rotate(String presentedToken, String deviceId) {
        String tokenHash = hash(presentedToken);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, session_id, user_id, customer_id, device_id, expires_at, consumed_at, revoked_at
            FROM public.mobile_auth_refresh_tokens
            WHERE token_hash = ?
            FOR UPDATE
            """, tokenHash);
        if (rows.isEmpty()) throw invalid();

        Map<String, Object> row = rows.get(0);
        UUID sessionId = (UUID) row.get("session_id");
        if (row.get("consumed_at") != null) {
            // A previously rotated token was reused: revoke every token in that session.
            jdbc.update("UPDATE public.mobile_auth_sessions SET revoked_at = COALESCE(revoked_at, now()) WHERE session_id = ?", sessionId);
            jdbc.update("UPDATE public.mobile_auth_refresh_tokens SET revoked_at = COALESCE(revoked_at, now()) WHERE session_id = ?", sessionId);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected. Please log in again.");
        }
        if (row.get("revoked_at") != null) throw invalid();
        List<Map<String, Object>> sessions = jdbc.queryForList(
                "SELECT revoked_at, expires_at FROM public.mobile_auth_sessions WHERE session_id = ? FOR UPDATE", sessionId);
        if (sessions.isEmpty() || sessions.get(0).get("revoked_at") != null) throw invalid();
        if (!constantTimeEquals(String.valueOf(row.get("device_id")), deviceId)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token is not valid for this device.");
        }
        Object expiryValue = row.get("expires_at");
        Instant expiresAt = expiryValue instanceof OffsetDateTime odt ? odt.toInstant()
                : expiryValue instanceof java.sql.Timestamp ts ? ts.toInstant()
                : ((java.util.Date) expiryValue).toInstant();
        if (!expiresAt.isAfter(Instant.now())) {
            jdbc.update("UPDATE public.mobile_auth_sessions SET revoked_at = now() WHERE session_id = ?", sessionId);
            jdbc.update("UPDATE public.mobile_auth_refresh_tokens SET revoked_at = now() WHERE session_id = ?", sessionId);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token expired. Please log in again.");
        }

        jdbc.update("UPDATE public.mobile_auth_refresh_tokens SET consumed_at = now() WHERE id = ? AND consumed_at IS NULL",
                ((Number) row.get("id")).longValue());
        String nextToken = newOpaqueToken();
        jdbc.update("""
            INSERT INTO public.mobile_auth_refresh_tokens
              (session_id, user_id, customer_id, device_id, token_hash, expires_at)
            VALUES (?, ?, ?, ?, ?, now() + (? * interval '1 second'))
            """, sessionId, ((Number) row.get("user_id")).longValue(),
                String.valueOf(row.get("customer_id")), deviceId, hash(nextToken), IDLE_TTL_SECONDS);

        jdbc.update("UPDATE public.mobile_auth_sessions SET expires_at = now() + (? * interval '1 second'), last_used_at = now() WHERE session_id = ?",
                IDLE_TTL_SECONDS, sessionId);
        String accessToken = jwt.createAccessToken(String.valueOf(row.get("customer_id")), "CU", "MOBILE", sessionId.toString());
        return new RefreshTokenResponse(accessToken, nextToken, "Bearer", jwt.getAccessTtlSeconds());
    }

    private String newOpaqueToken() {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable", ex); }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private AuthException invalid() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Invalid or revoked refresh token. Please log in again.");
    }
}
