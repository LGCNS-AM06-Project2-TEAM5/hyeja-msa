package com.hyeja.gateway.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/** Keeps monitoring Basic authentication separate from the Gateway JWT filter chain. */
@Configuration(proxyBeanMethods = false)
public class MonitoringSecurityConfiguration {
    /** Creates a monitoring-only account, or no account when the local password is unset. */
    @Bean
    ReactiveUserDetailsService monitoringUsers(
            @Value("${MONITORING_USERNAME:monitoring}") String username,
            @Value("${MONITORING_PASSWORD:}") String password) {
        if (password.isBlank()) return ignored -> reactor.core.publisher.Mono.empty();
        if (username.isBlank() || password.length() < 16) {
            throw new IllegalArgumentException("Monitoring credentials require a username and 16+ character password");
        }
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new MapReactiveUserDetailsService(User.withUsername(username)
                .password(encoder.encode(password)).roles("MONITORING").build());
    }

    /** Authenticates metric reads without accepting monitoring credentials for business routes. */
    @Bean
    @Order(-1)
    SecurityWebFilterChain monitoringSecurity(ServerHttpSecurity http, ReactiveUserDetailsService monitoringUsers) {
        return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/actuator/prometheus", "/actuator/prometheus/**"))
                .authenticationManager(new UserDetailsRepositoryReactiveAuthenticationManager(monitoringUsers))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .httpBasic(Customizer.withDefaults())
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.GET, "/actuator/prometheus").hasRole("MONITORING")
                        .anyExchange().denyAll())
                .build();
    }
}
