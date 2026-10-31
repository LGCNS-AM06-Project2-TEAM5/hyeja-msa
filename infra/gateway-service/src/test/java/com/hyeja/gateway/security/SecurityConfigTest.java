package com.hyeja.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;

class SecurityConfigTest {
    private static final String SECRET = "test-only-secret-key-at-least-32-bytes-long";
    private AnnotationConfigApplicationContext context;
    private WebTestClient client;
    private TokenBlacklist blacklist;

    @Configuration
    @EnableWebFluxSecurity
    static class TestConfig {
        @Bean JwtVerifier verifier() { return new JwtVerifier(SECRET); }
        @Bean TokenBlacklist blacklist() { return mock(TokenBlacklist.class); }
        @Bean WebHandler webHandler() {
            return exchange -> {
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);
                if (exchange.getRequest().getHeaders().getFirst("X-Member-Id") != null) {
                    exchange.getResponse().getHeaders().set("Verified-Member",
                            exchange.getRequest().getHeaders().getFirst("X-Member-Id"));
                }
                exchange.getResponse().getHeaders().set("Forwarded-Authorization",
                        exchange.getRequest().getHeaders().getFirst("Authorization") == null ? "none" :
                                exchange.getRequest().getHeaders().getFirst("Authorization"));
                return exchange.getResponse().setComplete();
            };
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(TestConfig.class, SecurityConfig.class);
        blacklist = context.getBean(TokenBlacklist.class);
        when(blacklist.contains(anyString())).thenReturn(Mono.just(false));
        client = WebTestClient.bindToApplicationContext(context)
                .configureClient().baseUrl("http://localhost:8000").build();
    }

    @org.junit.jupiter.api.AfterEach
    void close() { context.close(); }

    private String token(String role, Instant expiration) {
        return Jwts.builder().subject("10").claim("role", role)
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test
    void publicAndProtectedPaths() {
        client.post().uri("/api/members/login").exchange().expectStatus().isOk();
        client.get().uri("/api/policies/123").exchange().expectStatus().isOk();
        client.get().uri("/api/policies/search").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/policies/card-news").exchange().expectStatus().isUnauthorized();
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void validTokenIsForwardedAndSpoofedIdentityReplaced() {
        String token = token("USER", Instant.now().plusSeconds(600));
        client.get().uri("/api/members/me").header("Authorization", "Bearer " + token)
                .header("X-Member-Id", "999").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Verified-Member", "10")
                .expectHeader().valueEquals("Forwarded-Authorization", "Bearer " + token);
        client.get().uri("/api/regions").header("X-Member-Id", "999").exchange()
                .expectStatus().isOk().expectHeader().doesNotExist("Verified-Member");
    }

    @Test
    void invalidExpiredAndBlockedTokensAreRejectedEvenOnPublicPaths() {
        String token = token("USER", Instant.now().plusSeconds(600));
        client.get().uri("/api/regions").header("Authorization", "Bearer broken")
                .exchange().expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("COMMON_002");
        client.get().uri("/api/regions").header("Authorization", "Bearer " + token("USER", Instant.now().minusSeconds(60)))
                .exchange().expectStatus().isUnauthorized();
        when(blacklist.contains(token)).thenReturn(Mono.just(true));
        client.get().uri("/api/regions").header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void adminPathsRequireAdminRole() {
        String user = token("USER", Instant.now().plusSeconds(600));
        String admin = token("ADMIN", Instant.now().plusSeconds(600));
        for (String path : new String[]{"/api/policies/sync", "/api/notification/admin/send"}) {
            client.post().uri(path).header("Authorization", "Bearer " + user).exchange()
                    .expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("COMMON_004");
            client.post().uri(path).header("Authorization", "Bearer " + admin).exchange().expectStatus().isOk();
        }
    }

    @Test
    void redisFailureDoesNotAllowAuthenticatedRequest() {
        when(blacklist.contains(anyString())).thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));
        client.get().uri("/api/members/me").header("Authorization", "Bearer " + token("USER", Instant.now().plusSeconds(600)))
                .exchange().expectStatus().isEqualTo(503);
    }

    @Test
    void wrongSignatureMissingClaimsAndMalformedAuthorizationAreRejected() {
        String wrongSignature = Jwts.builder().subject("10").claim("role", "USER")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor("another-test-secret-key-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();
        String missingRole = Jwts.builder().subject("10")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        for (String value : new String[]{"Bearer " + wrongSignature, "Bearer " + missingRole,
                "Basic anything", "Bearer "}) {
            client.get().uri("/api/regions").header("Authorization", value)
                    .exchange().expectStatus().isUnauthorized();
        }
        verifyNoInteractions(blacklist);
    }

    @Test
    void corsPreflightRequiresAllowedOriginAndDoesNotRequireToken() {
        client.options().uri("/api/policies/search").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
        client.options().uri("/api/policies/search").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET").exchange().expectStatus().isForbidden();
        verifyNoInteractions(blacklist);
    }
}
