package com.hyeja.gateway.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, JwtVerifier verifier,
                                                 TokenBlacklist blacklist) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("http://localhost:5173"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(spec -> spec.requestCache(NoOpServerRequestCache.getInstance()))
                .cors(spec -> spec.configurationSource(source))
                .exceptionHandling(spec -> spec
                        .authenticationEntryPoint((exchange, error) -> SecurityError.unauthorized(exchange))
                        .accessDeniedHandler((exchange, error) -> SecurityError.write(exchange,
                                HttpStatus.FORBIDDEN, "COMMON_004", "접근 권한이 없습니다.")))
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/api/notification/admin/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/policies/sync").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/members", "/api/members/login",
                                "/api/members/email-verifications",
                                "/api/members/email-verifications/confirmation").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/policies/search", "/api/policies/card-news")
                                .authenticated()
                        .pathMatchers(HttpMethod.GET, "/api/members/find-email", "/api/regions",
                                "/api/terms/**", "/api/policies/housing", "/api/policies/card-news/guest",
                                "/api/policies/card-detail/*", "/api/policies/*", "/api/health").permitAll()
                        .anyExchange().authenticated())
                .addFilterAt(new JwtAuthenticationWebFilter(verifier, blacklist),
                        SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
