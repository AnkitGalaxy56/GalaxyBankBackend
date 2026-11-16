package com.abcbank.portal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final JdbcTemplate db;

    /**
     * The database is remote, so re-checking the stored token on every call costs a full round
     * trip. A successful check is remembered for a few seconds (keyed by a hash, never the raw
     * token). Revocation (logout, lock, new login) therefore takes effect within this window.
     */
    private static final long TOKEN_CACHE_MS = 10_000;
    private final ConcurrentHashMap<String, Long> validTokens = new ConcurrentHashMap<>();

    private static String digest(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public JwtFilter(JwtService jwt, JdbcTemplate db) {
        this.jwt = jwt;
        this.db = db;
    }

    /**
     * Theme endpoints are intentionally public bootstrap resources. Do not try to
     * validate an optional/stale bearer token on these routes: a bad token must
     * not turn a public theme request into a 401 and force the frontend to log out.
     * CORS preflight requests are also handled before authentication.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true;
        }

        if (!"GET".equalsIgnoreCase(req.getMethod())) {
            return false;
        }

        // getServletPath() excludes the configured /api/v1 context path.
        String path = req.getServletPath();
        return path.startsWith("/themes/")
                || path.equals("/themes")
                || path.matches("^/banks/[^/]+/theme$");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain
    ) throws ServletException, IOException {

        String header = req.getHeader("Authorization");

        // No token: let Spring Security decide whether this route is public.
        // This preserves 401/403 behavior instead of treating anonymous requests
        // as malformed JWTs.
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res);
            return;
        }

        try {
            String raw = header.substring(7).trim();
            if (raw.isEmpty()) {
                throw new IllegalArgumentException("Empty bearer token");
            }

            var claims = jwt.parse(raw);
            long userId = Long.parseLong(claims.getSubject());

            String cacheKey = digest(raw);
            Long checkedAt = validTokens.get(cacheKey);
            long now = System.currentTimeMillis();
            if (checkedAt == null || now - checkedAt > TOKEN_CACHE_MS) {
                Integer valid = db.queryForObject(
                        "select count(*) from public.\"MB001001\" " +
                                "where user_id=? and token=? and status='ACTIVE'",
                        Integer.class,
                        userId,
                        raw
                );

                if (valid == null || valid == 0) {
                    validTokens.remove(cacheKey);
                    throw new IllegalArgumentException("Token is revoked or user is inactive");
                }
                if (validTokens.size() > 5000) validTokens.clear();
                validTokens.put(cacheKey, now);
            }

            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("Token role is missing");
            }

            Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
            String normalizedRole = role.toUpperCase(Locale.ROOT);
            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizedRole));

            // The portal uses BANK_ADMIN/BANK_USER role names in route guards,
            // while the existing database stores ADMIN/STAFF. Publish both
            // compatible authorities without weakening authentication.
            if (normalizedRole.equals("ADMIN")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_BANK_ADMIN"));
            } else if (normalizedRole.equals("STAFF") || normalizedRole.equals("USER")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_BANK_USER"));
            } else if (normalizedRole.equals("SUPER_ADMIN")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    Long.toString(userId),
                    null,
                    authorities
            );
            authentication.setDetails(claims);
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"Invalid, expired, or revoked access token\"}");
            return;
        }

        chain.doFilter(req, res);
    }
}
