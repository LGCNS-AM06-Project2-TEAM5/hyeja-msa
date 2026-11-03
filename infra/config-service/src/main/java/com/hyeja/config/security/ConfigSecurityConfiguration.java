package com.hyeja.config.security;

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

    @Bean
    UserDetailsService configAdmin(
            @Value("${config-security.admin-username}") String username,
            @Value("${config-security.admin-password}") String password) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("Config Bus administrator credentials must not be blank");
        }
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password)).roles("CONFIG_ADMIN").build());
    }

    @Bean
    SecurityFilterChain configSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/actuator/busrefresh", "/actuator/busrefresh/**"))
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .httpBasic(Customizer.withDefaults())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/actuator/busrefresh", "/actuator/busrefresh/**")
                            .hasRole("CONFIG_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
                            .permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        // Preserve the existing Config Client read contract.
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
