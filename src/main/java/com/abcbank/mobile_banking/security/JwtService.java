package com.abcbank.mobile_banking.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlSeconds;
    private final long accessTtlSeconds;
    public JwtService(@Value("${app.security.jwt-secret}") String secret,
                      @Value("${app.security.registration-token-ttl-seconds:300}") long ttlSeconds,
                      @Value("${app.security.access-token-ttl-seconds:900}") long accessTtlSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.ttlSeconds=ttlSeconds; this.accessTtlSeconds=accessTtlSeconds;
    }
    public String createRegistrationToken(String customerId) {
        Instant now=Instant.now();
        return Jwts.builder().subject(customerId).claim("user_type", "CU").claim("scope", "registration:complete")
          .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttlSeconds))).signWith(key).compact();
    }
    /** Login token (web or mobile). scope=access */
    public String createAccessToken(String subject, String userType, String channel) {
        Instant now=Instant.now();
        return Jwts.builder().subject(subject).claim("user_type", userType).claim("scope", "access").claim("channel", channel)
          .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(accessTtlSeconds))).signWith(key).compact();
    }
    /** Forgot-MPIN token, valid only for the "set new MPIN" call. scope=mpin:reset */
    public String createMpinResetToken(String customerId, long ttl) {
        Instant now=Instant.now();
        return Jwts.builder().subject(customerId).claim("user_type", "CU").claim("scope", "mpin:reset")
          .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttl))).signWith(key).compact();
    }
    public long getAccessTtlSeconds(){return accessTtlSeconds;}
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public long getTtlSeconds(){return ttlSeconds;}
}