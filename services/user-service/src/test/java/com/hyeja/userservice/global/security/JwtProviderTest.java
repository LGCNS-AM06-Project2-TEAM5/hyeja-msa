package com.hyeja.userservice.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyeja.userservice.domain.member.entity.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtProviderTest {

    private final JwtProvider jwtProvider =
            new JwtProvider("hyeja-test-only-jwt-secret-key-0123456789");

    @Test
    void createdTokenContainsMemberIdAndRole() {
        Claims claims = jwtProvider.parse(jwtProvider.createAccessToken(member(1L)));

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                .isEqualTo(300 * 60 * 1000L);
    }

    @Test
    void rejectsTokenSignedWithOtherKey() {
        String forged = new JwtProvider("other-secret-key-for-forged-token-0123456789")
                .createAccessToken(member(1L));

        assertThatThrownBy(() -> jwtProvider.parse(forged))
                .isInstanceOf(JwtException.class);
    }

    private Member member(Long memberId) {
        Member member = Member.builder()
                .email("hyeja@example.com")
                .password("encoded")
                .nickname("민지")
                .build();
        ReflectionTestUtils.setField(member, "memberId", memberId);
        return member;
    }
}
