package com.hyeja.userservice.global.config;

import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.security.JwtAuthenticationFilter;
import com.hyeja.userservice.global.security.JwtProvider;
import com.hyeja.userservice.global.security.TokenBlacklist;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final TokenBlacklist tokenBlacklist;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/members",
                                "/api/members/login",
                                "/api/members/email-verifications",
                                "/api/members/email-verifications/confirmation").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/members/find-email",
                                "/health-check",
                                "/actuator/health",
                                "/actuator/health/**").permitAll()
                        .requestMatchers("/h2-console/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, error) ->
                                writeError(response, ErrorStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, error) ->
                                writeError(response, ErrorStatus.FORBIDDEN)))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, tokenBlacklist),
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }

    private static void writeError(HttpServletResponse response, ErrorStatus error) throws IOException {
        response.setStatus(error.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"isSuccess\":false,\"code\":\"%s\",\"message\":\"%s\",\"result\":null}"
                        .formatted(error.getCode(), error.getMessage())
        );
    }
}
