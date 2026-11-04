package com.hyeja.engagementservice.security;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public final class InternalTokenFixture {
    public static final String SECRET = "test-only-internal-key-at-least-32-bytes-long";
    private InternalTokenFixture() {}
    public static JwtBuilder claims(long memberId) {
        Instant now = Instant.now();
        return Jwts.builder().issuer("gateway-service").audience().add("engagement-service").and()
                .subject(Long.toString(memberId)).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(120)));
    }
    public static String sign(JwtBuilder builder) { return sign(builder, SECRET); }
    public static String sign(JwtBuilder builder, String secret) {
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }
    public static String valid(long memberId) { return sign(claims(memberId)); }
}
