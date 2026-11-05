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
    public JwtAuthenticationFilter(JwtService jwtService) { this.jwtService = jwtService; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        logger.warn("AUTH DEBUG " + request.getMethod() + " " + request.getRequestURI()
                + " | Authorization header present: " + (header != null));
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(header.substring(7).trim());
                String subject = claims.getSubject();
                String scope = claims.get("scope", String.class);
                String userType = claims.get("user_type", String.class);
                logger.warn("AUTH DEBUG token ok: subject=" + subject + " scope=" + scope + " userType=" + userType);
                if (subject != null && "CU".equals(userType) && "registration:complete".equals(scope)) {
                    var auth = new UsernamePasswordAuthenticationToken(subject, null, List.of(new SimpleGrantedAuthority("SCOPE_registration:complete")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else if (subject != null && "CU".equals(userType) && "mpin:reset".equals(scope)) {
                    var auth = new UsernamePasswordAuthenticationToken(subject, null, List.of(new SimpleGrantedAuthority("SCOPE_mpin:reset")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else if (subject != null && userType != null && "access".equals(scope)) {
                    var auth = new UsernamePasswordAuthenticationToken(subject, null,
                        List.of(new SimpleGrantedAuthority("SCOPE_access"), new SimpleGrantedAuthority("ROLE_" + userType)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                logger.warn("AUTH DEBUG JWT REJECTED: " + ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}