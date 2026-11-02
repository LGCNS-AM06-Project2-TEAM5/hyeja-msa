package com.hyeja.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Only the gateway and engagement receive this key; never reuse the user JWT key. */
@Component
public class EngagementTokenIssuer {
    public static final String HEADER = "X-Gateway-Assertion";
    private final SecretKey key;

    public EngagementTokenIssuer(@Value("${internal-auth.secret}") String secret,
                                 @Value("${jwt.secret}") String userSecret) {
        if (secret == null || secret.isBlank() || secret.equals(userSecret)) {
            throw new IllegalArgumentException("A separate internal authentication key is required");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(long memberId) {
        if (memberId <= 0) throw new IllegalArgumentException("Invalid member ID");
        Instant now = Instant.now();
        return Jwts.builder().issuer("gateway-service").audience().add("engagement-service").and()
                .subject(Long.toString(memberId)).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(120)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }
}
