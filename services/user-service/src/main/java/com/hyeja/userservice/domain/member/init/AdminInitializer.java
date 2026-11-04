package com.hyeja.userservice.domain.member.init;

import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 기동 시 환경변수로 전달된 공용 관리자 계정을 한 번만 생성합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String email;

    @Value("${admin.password:}")
    private String password;

    @Override
    public void run(String... args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("[AdminInitializer] 관리자 환경변수가 비어 있어 계정 생성을 건너뜁니다.");
            return;
        }

        if (memberRepository.existsByEmail(email)) {
            log.info("[AdminInitializer] 관리자 이메일이 이미 등록되어 있어 계정 생성을 건너뜁니다.");
            return;
        }

        memberRepository.save(Member.admin(email, passwordEncoder.encode(password)));
        log.info("[AdminInitializer] 공용 관리자 계정을 생성했습니다.");
    }
}
