package com.abcbank.mobile_banking.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import io.jsonwebtoken.Claims;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlSeconds;
    public JwtService(@Value("${app.security.jwt-secret}") String secret,
                      @Value("${app.security.registration-token-ttl-seconds:300}") long ttlSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.ttlSeconds=ttlSeconds;
    }
    public String createRegistrationToken(String customerId) {
        Instant now=Instant.now();
        return Jwts.builder().subject(customerId).claim("user_type", "CU").claim("scope", "registration:complete")
          .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttlSeconds))).signWith(key).compact();
    }
    public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public long getTtlSeconds(){return ttlSeconds;}
}
