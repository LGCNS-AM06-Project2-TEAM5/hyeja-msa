package com.hyeja.engagementservice.global.config;

import com.hyeja.engagementservice.global.security.GatewayAuthenticationFilter;
import com.hyeja.engagementservice.global.security.GatewayTokenVerifier;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain engagementSecurity(HttpSecurity http, GatewayTokenVerifier verifier) throws Exception {
        return http.csrf(spec -> spec.disable())
                .formLogin(spec -> spec.disable()).httpBasic(spec -> spec.disable())
                .logout(spec -> spec.disable()).requestCache(spec -> spec.disable())
                .sessionManagement(spec -> spec.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(spec -> spec.authenticationEntryPoint((request, response, error) ->
                        GatewayAuthenticationFilter.unauthorized(response)))
                .authorizeHttpRequests(spec -> spec
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/api/health", "/health-check").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new GatewayAuthenticationFilter(verifier), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
