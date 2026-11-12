
package com.abcbank.mobile_banking.security;

import java.io.IOException;
import java.util.List;

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

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                String token = header.substring(7).trim();
                Claims claims = jwtService.parse(token);

                String subject = claims.getSubject();
                String userType = claims.get("user_type", String.class);
                String scope = claims.get("scope", String.class);

                if (subject != null && !subject.isBlank()
                        && "CU".equals(userType)) {

                    String authority = null;

                    if ("registration:complete".equals(scope)) {
                        authority = "SCOPE_registration:complete";
                    } else if ("mpin:reset".equals(scope)) {
                        authority = "SCOPE_mpin:reset";
                    } else if ("fund:transfer".equals(scope)
                            || "access".equals(scope)) {
                        authority = "SCOPE_fund:transfer";
                    }

                    if (authority != null) {
                        var authentication =
                                new UsernamePasswordAuthenticationToken(
                                        subject,
                                        null,
                                        List.of(new SimpleGrantedAuthority(authority))
                                );

                        SecurityContextHolder.getContext()
                                .setAuthentication(authentication);
                    }
                }

            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
