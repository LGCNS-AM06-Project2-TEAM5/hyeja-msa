package com.hyeja.engagementservice.global.security;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GatewayTokenVerifier {
    public static final String HEADER = "X-Gateway-Assertion";
    private final JwtParser parser;

    public GatewayTokenVerifier(@Value("${internal-auth.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Internal authentication key is required");
        }
        var key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        parser = Jwts.parser().verifyWith(key).requireIssuer("gateway-service")
                .sig().clear().add(Jwts.SIG.HS256).and().build();
    }

    public long verify(String token) {
        var claims = parser.parseSignedClaims(token).getPayload();
        if (!Set.of("engagement-service").equals(claims.getAudience())
                || claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new IllegalArgumentException("Missing or invalid gateway claims");
        }
        Instant issued = claims.getIssuedAt().toInstant();
        Instant expires = claims.getExpiration().toInstant();
        if (issued.isAfter(Instant.now().plusSeconds(5)) || !expires.isAfter(issued)
                || expires.isAfter(issued.plusSeconds(120))) {
            throw new IllegalArgumentException("Invalid gateway token lifetime");
        }
        long memberId = Long.parseLong(claims.getSubject());
        if (memberId <= 0) throw new IllegalArgumentException("Invalid member ID");
        return memberId;
    }
}
