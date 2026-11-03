package com.hyeja.engagementservice.global.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Registered only in the security chain, never as a servlet filter bean. */
public final class GatewayAuthenticationFilter extends OncePerRequestFilter {
    private final GatewayTokenVerifier verifier;

    public GatewayAuthenticationFilter(GatewayTokenVerifier verifier) { this.verifier = verifier; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/internal") || path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var values = Collections.list(request.getHeaders(GatewayTokenVerifier.HEADER));
        if (values.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        long memberId;
        try {
            if (values.size() != 1 || values.get(0).isBlank()) {
                throw new IllegalArgumentException("Invalid gateway header");
            }
            memberId = verifier.verify(values.get(0));
        } catch (JwtException | IllegalArgumentException exception) {
            unauthorized(response);
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"isSuccess\":false,\"code\":\"COMMON_002\","
                + "\"message\":\"인증이 필요합니다.\",\"result\":null}");
    }
}
