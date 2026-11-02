package com.hyeja.userservice.domain.member.service;

import com.hyeja.userservice.domain.member.dto.EmailVerificationResponseDTO;
import com.hyeja.userservice.domain.member.repository.MemberRepository;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.GeneralException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration VERIFIED_TTL = Duration.ofMinutes(30);
    static final int MAX_FAILURES = 5;
    static final Duration LOCK_TTL = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MAIL_TEMPLATE = readTemplate("mail/verification-code.html");

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final MemberRepository memberRepository;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public long send(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new GeneralException(ErrorStatus.MEMBER_EMAIL_DUPLICATED);
        }
        checkNotLocked(email);
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(key("cooldown", email), "1", RESEND_COOLDOWN))) {
            throw new GeneralException(ErrorStatus.VERIFY_RESEND_TOO_SOON);
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            deliver(email, code);
        } catch (MailException e) {
            log.warn("인증 메일 발송 실패: {}", email, e);
            redisTemplate.delete(key("cooldown", email));
            throw new GeneralException(ErrorStatus.MAIL_SEND_FAILED);
        }

        redisTemplate.opsForValue().set(key("code", email), code, CODE_TTL);
        redisTemplate.delete(key("verified", email));
        return CODE_TTL.toSeconds();
    }

    public void confirm(String email, String code) {
        checkNotLocked(email);
        String saved = redisTemplate.opsForValue().get(key("code", email));
        if (saved == null) {
            throw new GeneralException(ErrorStatus.VERIFY_CODE_EXPIRED);
        }

        long attempts = countAttempt(email);
        if (attempts > MAX_FAILURES || !saved.equals(code)) {
            long remaining = MAX_FAILURES - attempts;
            if (remaining <= 0) {
                redisTemplate.opsForValue().set(key("locked", email), "1", LOCK_TTL);
                redisTemplate.delete(key("code", email));
                throw new GeneralException(ErrorStatus.VERIFY_TOO_MANY_FAILURES);
            }
            throw new GeneralException(
                    ErrorStatus.VERIFY_CODE_MISMATCH,
                    new EmailVerificationResponseDTO.MismatchDTO(remaining)
            );
        }

        redisTemplate.delete(key("code", email));
        redisTemplate.delete(key("failures", email));
        redisTemplate.opsForValue().set(key("verified", email), "1", VERIFIED_TTL);
    }

    public void checkVerified(String email) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key("verified", email)))) {
            throw new GeneralException(ErrorStatus.VERIFY_REQUIRED);
        }
    }

    public void clearVerified(String email) {
        redisTemplate.delete(key("verified", email));
    }

    private long countAttempt(String email) {
        Long attempts = redisTemplate.opsForValue().increment(key("failures", email));
        if (attempts == null) {
            return MAX_FAILURES + 1L;
        }
        if (attempts == 1) {
            redisTemplate.expire(key("failures", email), LOCK_TTL);
        }
        return attempts;
    }

    private void checkNotLocked(String email) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key("locked", email)))) {
            throw new GeneralException(ErrorStatus.VERIFY_TOO_MANY_FAILURES);
        }
    }

    private void deliver(String email, String code) {
        if (mailFrom.isBlank()) {
            log.info("[로컬 개발] MAIL_USERNAME이 없어 메일 대신 로그로 출력합니다. {} 인증 코드: {}", email, code);
            return;
        }

        String minutes = String.valueOf(CODE_TTL.toMinutes());
        String html = MAIL_TEMPLATE.replace("{{code}}", code).replace("{{minutes}}", minutes);
        String plain = "인증 코드: " + code + "\n\n" + minutes + "분 안에 회원가입 화면에 입력해 주세요.";

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject("[혜자] 회원가입 인증 코드");
            helper.setText(plain, html);
        } catch (MessagingException e) {
            throw new MailPreparationException("인증 메일 작성 실패", e);
        }
        mailSender.send(message);
    }

    private static String readTemplate(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String key(String type, String email) {
        return "email-verification:" + type + ":" + email;
    }
}
