package com.hyeja.userservice.domain.member.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.member.enums.Role;
import com.hyeja.userservice.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminInitializerTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminInitializer initializer = new AdminInitializer(memberRepository, passwordEncoder);

    @Test
    void createsAdminWithEncodedPassword() {
        setAdmin("admin@hyeja.test", "admin-pass");
        when(passwordEncoder.encode("admin-pass")).thenReturn("encoded");

        initializer.run();

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@hyeja.test");
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded");
        assertThat(saved.getValue().getNickname()).isEqualTo("관리자");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void skipsWhenEmailOrPasswordIsBlank() {
        setAdmin("", "admin-pass");
        initializer.run();
        setAdmin("admin@hyeja.test", "");
        initializer.run();

        verify(memberRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void skipsWhenEmailAlreadyExists() {
        setAdmin("admin@hyeja.test", "admin-pass");
        when(memberRepository.existsByEmail("admin@hyeja.test")).thenReturn(true);

        initializer.run();

        verify(memberRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    private void setAdmin(String email, String password) {
        ReflectionTestUtils.setField(initializer, "email", email);
        ReflectionTestUtils.setField(initializer, "password", password);
    }
}
