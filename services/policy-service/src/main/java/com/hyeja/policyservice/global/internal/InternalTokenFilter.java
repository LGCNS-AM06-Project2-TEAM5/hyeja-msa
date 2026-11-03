package com.hyeja.policyservice.global.internal;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

// 다른 서비스가 부르는 내부 API(/internal/**)는 X-Internal-Token이 공유 비밀값과 같을 때만 통과시킵니다(11-03 팀 결정).
// 값은 Config Server의 hyeja.internal.token에서 받고, 비어 있으면 내부 API를 모두 막습니다.
// /api/**는 지금처럼 Gateway 인증을 따르므로 이 필터가 보지 않습니다.
@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private final byte[] expectedToken;
    private final JsonMapper jsonMapper;

    public InternalTokenFilter(@Value("${hyeja.internal.token:}") String token, JsonMapper jsonMapper) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
        this.jsonMapper = jsonMapper;
        if (token.isBlank()) {
            log.warn("hyeja.internal.token이 없어 내부 API(/internal/**) 요청을 모두 401로 막습니다.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isValid(request.getHeader(InternalToken.HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        ErrorStatus error = ErrorStatus.UNAUTHORIZED;
        response.setStatus(error.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(
                ApiResponse.onFailure(error.getCode(), error.getMessage(), null)));
    }

    private boolean isValid(String token) {
        if (expectedToken.length == 0 || token == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
    }
}
