package com.abcbank.portal.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
 @Value("${portal.jwt.secret}") private String secret;
 @Value("${portal.jwt.ttl-minutes:30}") private long ttl;
 private SecretKey key(){ if(secret==null || secret.length()<32) throw new IllegalStateException("PORTAL_JWT_SECRET must contain at least 32 characters"); return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
 public String create(long userId,String username,String role,String branch){ Instant now=Instant.now(); return Jwts.builder().subject(Long.toString(userId)).claim("username",username).claim("role",role).claim("branch",branch).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttl*60))).signWith(key()).compact(); }
 public io.jsonwebtoken.Claims parse(String token){ return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload(); }
}
