package com.hyeja.config.security;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class ConfigSecurityConfiguration {
    private final UserDetailsService accounts;

    public ConfigSecurityConfiguration(@Value("${encrypt.key}") String encryptionKey,
            @Value("${config-security.reader-username}") String reader,
            @Value("${config-security.reader-password}") String readerPassword,
            @Value("${config-security.admin-username}") String admin,
            @Value("${config-security.admin-password}") String adminPassword) {
        if (encryptionKey == null || encryptionKey.isBlank()
                || encryptionKey.getBytes(StandardCharsets.UTF_8).length < 32
                || encryptionKey.startsWith("{cipher}") || encryptionKey.contains("${")) {
            throw new IllegalArgumentException("CONFIG_ENCRYPT_KEY must contain at least 32 UTF-8 bytes");
        }
        validateUsername(reader);
        validateUsername(admin);
        validatePassword(readerPassword);
        validatePassword(adminPassword);
        if (reader.equals(admin) || readerPassword.equals(adminPassword)
                || encryptionKey.equals(readerPassword) || encryptionKey.equals(adminPassword)) {
            throw new IllegalArgumentException("Use separate Config accounts, passwords and encryption key");
        }
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        accounts = new InMemoryUserDetailsManager(
                User.withUsername(reader).password(encoder.encode(readerPassword)).roles("CONFIG_READER").build(),
                User.withUsername(admin).password(encoder.encode(adminPassword))
                        .roles("CONFIG_READER", "CONFIG_ADMIN").build());
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank() || username.contains(":")
                || username.contains("${") || username.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid Config authentication username");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 16
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.contains("${") || password.startsWith("{cipher}")) {
            throw new IllegalArgumentException("Config passwords must contain 16+ characters and at most 72 UTF-8 bytes");
        }
    }

    @Bean
    UserDetailsService configAccounts() { return accounts; }

    @Bean
    SecurityFilterChain configSecurity(HttpSecurity http) throws Exception {
        return http.csrf(spec -> spec.disable())
                .formLogin(spec -> spec.disable()).logout(spec -> spec.disable())
                .httpBasic(Customizer.withDefaults()).requestCache(spec -> spec.disable())
                .sessionManagement(spec -> spec.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(spec -> spec
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/encrypt", "/encrypt/**", "/decrypt", "/decrypt/**", "/key", "/key/**")
                            .hasRole("CONFIG_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/**").hasRole("CONFIG_READER")
                        .anyRequest().denyAll())
                .build();
    }
}
