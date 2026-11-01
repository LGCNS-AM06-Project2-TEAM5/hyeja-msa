package com.hyeja.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtVerifier {
    private final SecretKey key;

    public JwtVerifier(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Identity verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        long memberId = Long.parseLong(claims.getSubject());
        String role = claims.get("role", String.class);
        if (memberId <= 0 || role == null || role.isBlank() || claims.getExpiration() == null) {
            throw new IllegalArgumentException("Missing or invalid identity claims");
        }
        return new Identity(memberId, role);
    }

    public record Identity(long memberId, String role) {}
}
