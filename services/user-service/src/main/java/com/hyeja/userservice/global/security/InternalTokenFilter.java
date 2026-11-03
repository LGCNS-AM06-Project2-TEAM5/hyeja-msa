package com.hyeja.userservice.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// 서비스 간 호출(/internal/**)의 X-Internal-Token 헤더를 공유 비밀값과 비교합니다.
// policy-service와 같이 값이 비어 있으면 서버는 뜨고 내부 API만 모두 401로 막습니다.
@Slf4j
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    private static final int MIN_TOKEN_BYTES = 32;

    private final byte[] expectedToken;

    public InternalTokenFilter(String token) {
        String value = token == null ? "" : token;
        this.expectedToken = value.getBytes(StandardCharsets.UTF_8);
        if (value.isBlank()) {
            log.warn("hyeja.internal.token이 없어 내부 API(/internal/**) 요청을 모두 401로 막습니다.");
        } else if (expectedToken.length < MIN_TOKEN_BYTES) {
            throw new IllegalStateException("hyeja.internal.token은 32바이트 이상이어야 합니다.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (matches(request.getHeader(HEADER))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "internal",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String token) {
        if (expectedToken.length == 0 || token == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
    }
}
