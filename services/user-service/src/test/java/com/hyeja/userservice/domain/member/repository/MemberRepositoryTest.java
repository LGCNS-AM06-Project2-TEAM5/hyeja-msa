package com.hyeja.userservice.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.member.enums.Role;
import com.hyeja.userservice.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class MemberRepositoryTest {

    // Repository 주입과 도우미 메서드
    @Autowired
    private MemberRepository memberRepository;

    private Member member(String email, String nickname) {
        return Member.builder()
                .email(email)
                .password("encoded-password")
                .nickname(nickname)
                .build();
    }

    @Test
    @DisplayName("회원을 저장하면 USER 역할과 생성·수정 시각이 채워진다")
    void save() {
        Member saved = memberRepository.saveAndFlush(member("user@hyeja.com", "혜자"));

        assertThat(saved.getMemberId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("이메일과 닉네임으로 회원을 찾고 존재 여부를 확인한다")
    void findAndExists() {
        memberRepository.saveAndFlush(member("user@hyeja.com", "혜자"));

        assertThat(memberRepository.existsByEmail("user@hyeja.com")).isTrue();
        assertThat(memberRepository.existsByEmail("none@hyeja.com")).isFalse();
        assertThat(memberRepository.existsByNickname("혜자")).isTrue();
        assertThat(memberRepository.findByEmail("user@hyeja.com"))
                .hasValueSatisfying(found -> assertThat(found.getNickname()).isEqualTo("혜자"));
        assertThat(memberRepository.findByNickname("혜자"))
                .hasValueSatisfying(found -> assertThat(found.getEmail()).isEqualTo("user@hyeja.com"));
    }

    @Test
    @DisplayName("같은 이메일로 다시 저장하면 예외가 난다")
    void duplicateEmail() {
        memberRepository.saveAndFlush(member("user@hyeja.com", "혜자"));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member("user@hyeja.com", "다른닉네임")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 닉네임으로 다시 저장하면 예외가 난다")
    void duplicateNickname() {
        memberRepository.saveAndFlush(member("user@hyeja.com", "혜자"));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member("other@hyeja.com", "혜자")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
