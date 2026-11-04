package com.hyeja.userservice.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hyeja.userservice.domain.member.entity.Member;
import jakarta.servlet.FilterChain;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtAuthenticationFilterTest {

    private final JwtProvider jwtProvider =
            new JwtProvider("hyeja-test-only-jwt-secret-key-0123456789");
    private final TokenBlacklist tokenBlacklist = mock(TokenBlacklist.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, tokenBlacklist);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidBearerToken() throws Exception {
        String token = jwtProvider.createAccessToken(member(1L));
        when(tokenBlacklist.contains(token)).thenReturn(false);
        MockHttpServletRequest request = requestWith(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getCredentials()).isEqualTo(token);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresMalformedAndBlacklistedTokens() throws Exception {
        FilterChain malformedChain = mock(FilterChain.class);
        filter.doFilterInternal(requestWith("broken"), new MockHttpServletResponse(), malformedChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        String token = jwtProvider.createAccessToken(member(1L));
        when(tokenBlacklist.contains(token)).thenReturn(true);
        filter.doFilterInternal(requestWith(token), new MockHttpServletResponse(), mock(FilterChain.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private MockHttpServletRequest requestWith(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private Member member(Long memberId) {
        Member member = Member.builder()
                .email("hyeja@example.com")
                .password("encoded-password")
                .nickname("민지")
                .build();
        ReflectionTestUtils.setField(member, "memberId", Objects.requireNonNull(memberId));
        return member;
    }
}
