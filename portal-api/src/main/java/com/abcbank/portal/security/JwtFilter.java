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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final JdbcTemplate db;

    public JwtFilter(JwtService jwt, JdbcTemplate db) {
        this.jwt = jwt;
        this.db = db;
    }

    /**
     * Theme endpoints are public bootstrap resources. Do not validate an optional
     * stale bearer token on these routes. CORS preflight is also handled before auth.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true;
        }
        if (!"GET".equalsIgnoreCase(req.getMethod())) {
            return false;
        }
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

            // Always compare against the currently active database token. Do not
            // cache successful checks: a new-device login must invalidate the old
            // device immediately, including when it refreshes or calls an API.
            Integer valid = db.queryForObject(
                    "select count(*) from public.\"MB001001\" " +
                            "where user_id=? and token=? and status='ACTIVE'",
                    Integer.class,
                    userId,
                    raw
            );
            if (valid == null || valid == 0) {
                throw new IllegalArgumentException("Token is revoked or user is inactive");
            }

            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("Token role is missing");
            }

            Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
            String normalizedRole = role.toUpperCase(Locale.ROOT);
            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizedRole));
            if (normalizedRole.equals("ADMIN")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_BANK_ADMIN"));
            } else if (normalizedRole.equals("STAFF") || normalizedRole.equals("USER")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_BANK_USER"));
            } else if (normalizedRole.equals("SUPER_ADMIN")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            var authentication = new UsernamePasswordAuthenticationToken(
                    Long.toString(userId), null, authorities
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
