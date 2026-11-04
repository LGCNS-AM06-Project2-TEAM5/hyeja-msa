package com.hyeja.recommendationservice.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Isolates metrics authentication from business JWT and Config Server credentials. */
@Configuration(proxyBeanMethods = false)
public class MonitoringSecurityConfiguration {
    /**
     * Creates the read-only monitoring account. An unset password disables access,
     * allowing local tests to run without creating a default or shared credential.
     */
    @Bean
    UserDetailsService monitoringUsers(
            @Value("${MONITORING_USERNAME:monitoring}") String username,
            @Value("${MONITORING_PASSWORD:}") String password) {
        if (password.isBlank()) return new InMemoryUserDetailsManager();
        if (username.isBlank() || password.length() < 16) {
            throw new IllegalArgumentException("Monitoring credentials require a username and 16+ character password");
        }
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password)).roles("MONITORING").build());
    }

    /** Uses an isolated provider/encoder so a business BCrypt bean cannot reinterpret monitoring hashes. */
    @Bean
    @Order(0)
    SecurityFilterChain monitoringSecurity(HttpSecurity http, UserDetailsService monitoringUsers) throws Exception {
        var provider = new DaoAuthenticationProvider(monitoringUsers);
        provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        return http.securityMatcher("/actuator/prometheus", "/actuator/prometheus/**")
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable()).logout(logout -> logout.disable())
                .httpBasic(Customizer.withDefaults()).requestCache(cache -> cache.disable())
                .authenticationManager(new ProviderManager(provider))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasRole("MONITORING")
                        .anyRequest().denyAll())
                .build();
    }
}
