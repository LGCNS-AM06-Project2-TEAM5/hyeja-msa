package com.hyeja.config.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfigAccessProperties.class)
public class ConfigSecurityConfiguration {
    private final UserDetailsService accounts;
    private final ConfigAccessProperties access;

    public ConfigSecurityConfiguration(@Value("${encrypt.key}") String encryptionKey,
            @Value("${server.ssl.enabled}") boolean tlsEnabled, ConfigAccessProperties access) {
        if (!tlsEnabled) throw new IllegalArgumentException("Config Server requires HTTPS");
        this.access = access;
        if (encryptionKey == null || encryptionKey.isBlank()
                || encryptionKey.getBytes(StandardCharsets.UTF_8).length < 32
                || encryptionKey.startsWith("{cipher}") || encryptionKey.contains("${")) {
            throw new IllegalArgumentException("CONFIG_ENCRYPT_KEY must contain at least 32 UTF-8 bytes");
        }
        String admin = access.adminUsername();
        String adminPassword = access.adminPassword();
        validateUsername(admin);
        validatePassword(adminPassword);
        if (access.readers() == null || access.readers().isEmpty()
                || access.allowedProfiles() == null || access.allowedProfiles().isEmpty()
                || access.allowedLabels() == null || access.allowedLabels().isEmpty())
            throw new IllegalArgumentException("Config application accounts, profiles and labels are required");
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var users = new ArrayList<UserDetails>();
        var usernames = new HashSet<String>();
        var passwords = new HashSet<String>();
        usernames.add(admin);
        passwords.add(encryptionKey);
        if (!passwords.add(adminPassword)) throw new IllegalArgumentException("Use separate Config secrets");
        users.add(User.withUsername(admin).password(encoder.encode(adminPassword)).roles("CONFIG_ADMIN").build());
        access.readers().forEach((application, reader) -> {
            if (!application.matches("[a-z][a-z0-9-]*") || application.equals("application")
                    || application.equals("config-service") || reader == null)
                throw new IllegalArgumentException("Invalid Config application scope");
            validateUsername(reader.username());
            validatePassword(reader.password());
            if (!usernames.add(reader.username()) || !passwords.add(reader.password()))
                throw new IllegalArgumentException("Each Config account requires a distinct username and password");
            users.add(User.withUsername(reader.username()).password(encoder.encode(reader.password()))
                    .authorities("ROLE_CONFIG_READER", "CONFIG_APP:" + application).build());
        });
        accounts = new InMemoryUserDetailsManager(users);
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
                        .requestMatchers(HttpMethod.GET, "/**").access(new ApplicationConfigAuthorizationManager(access))
                        .anyRequest().denyAll())
                .build();
    }
}
