package com.hyeja.userservice.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalTokenFilterTest {

    private static final String TOKEN = "hyeja-test-only-internal-token-0123456789";

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("비밀값이 32바이트보다 짧으면 생성되지 않는다")
    void rejectsShortToken() {
        assertThatThrownBy(() -> new InternalTokenFilter("short-token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("비밀값이 비어 있으면 생성은 되지만 어떤 헤더도 인증하지 않는다")
    void blankTokenAuthenticatesNothing() throws Exception {
        doFilter(new InternalTokenFilter(""), "");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        doFilter(new InternalTokenFilter(null), TOKEN);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("헤더가 비밀값과 같으면 ROLE_INTERNAL로 인증한다")
    void matchingTokenAuthenticatesInternal() throws Exception {
        doFilter(new InternalTokenFilter(TOKEN), TOKEN);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_INTERNAL");
    }

    @Test
    @DisplayName("헤더가 맞지 않으면 앞서 설정된 사용자 인증을 지운다")
    void mismatchClearsExistingAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        doFilter(new InternalTokenFilter(TOKEN), "wrong-token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void doFilter(InternalTokenFilter filter, String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/members/1/profile");
        request.addHeader(InternalTokenFilter.HEADER, header);
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }
}
