
package com.abcbank.mobile_banking.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            JdbcTemplate jdbc) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        try {
            if (header != null
                    && header.startsWith("Bearer ")
                    && SecurityContextHolder.getContext()
                            .getAuthentication() == null) {

                String token = header.substring(7).trim();

                Claims claims = jwtService.parse(token);

                String subject = claims.getSubject();
                String userType =
                        claims.get("user_type", String.class);
                String scope =
                        claims.get("scope", String.class);
                String sessionId =
                        claims.get("sid", String.class);

                boolean activeSession = true;

                if (sessionId != null && !sessionId.isBlank()) {

                    activeSession = Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    """
                                    SELECT EXISTS (
                                        SELECT 1
                                        FROM public.mobile_auth_sessions
                                        WHERE session_id = ?
                                          AND revoked_at IS NULL
                                          AND expires_at > now()
                                    )
                                    """,
                                    Boolean.class,
                                    UUID.fromString(sessionId)
                            )
                    );
                }

                if (activeSession
                        && subject != null
                        && !subject.isBlank()
                        && "CU".equals(userType)) {

                    List<SimpleGrantedAuthority> authorities =
                            new ArrayList<>();

                    // Customer role
                    authorities.add(
                            new SimpleGrantedAuthority("ROLE_CUSTOMER"));

                    if ("registration:complete".equals(scope)) {

                        authorities.add(
                                new SimpleGrantedAuthority(
                                        "SCOPE_registration:complete"));

                    } else if ("mpin:reset".equals(scope)) {

                        authorities.add(
                                new SimpleGrantedAuthority(
                                        "SCOPE_mpin:reset"));

                    } else if ("fund:transfer".equals(scope)
                            || "access".equals(scope)) {

                        authorities.add(
                                new SimpleGrantedAuthority(
                                        "SCOPE_fund:transfer"));
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    subject,
                                    null,
                                    authorities
                            );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }

        } catch (JwtException | IllegalArgumentException ex) {

            System.out.println(
                    "JWT AUTHENTICATION ERROR: " + ex.getMessage());

            SecurityContextHolder.clearContext();

        } catch (Exception ex) {

            System.out.println(
                    "AUTHENTICATION FILTER ERROR: " + ex.getMessage());

            SecurityContextHolder.clearContext();
        }

        // TEMPORARY DEBUGGING INFORMATION
        System.out.println("========== JWT DEBUG ==========");
        System.out.println("REQUEST URI: " + request.getRequestURI());
        System.out.println("REQUEST METHOD: " + request.getMethod());

        var authentication =
                SecurityContextHolder.getContext().getAuthentication();

        System.out.println("AUTHENTICATION: " + authentication);

        if (authentication != null) {
            System.out.println(
                    "AUTHORITIES: " + authentication.getAuthorities());
        } else {
            System.out.println("AUTHORITIES: NONE");
        }

        System.out.println("===============================");

        chain.doFilter(request, response);
    }
}
