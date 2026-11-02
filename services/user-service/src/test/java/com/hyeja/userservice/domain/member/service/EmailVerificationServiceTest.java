package com.hyeja.userservice.domain.member.service;

import com.hyeja.userservice.domain.member.repository.MemberRepository;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.GeneralException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceTest {

    private static final String EMAIL = "hyeja@example.com";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private MemberRepository memberRepository;
    @InjectMocks
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        ReflectionTestUtils.setField(service, "mailFrom", "hyeja.team@gmail.com");
    }

    @Test
    void sendMailsSixDigitCodeAndStoresItForFiveMinutes() throws Exception {
        when(values.setIfAbsent("email-verification:cooldown:" + EMAIL, "1", Duration.ofSeconds(60)))
                .thenReturn(true);

        long expiresIn = service.send(EMAIL);

        assertThat(expiresIn).isEqualTo(300);
        ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getAllRecipients()).containsExactly(new InternetAddress(EMAIL));
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("email-verification:code:" + EMAIL), code.capture(), eq(Duration.ofMinutes(5)));
        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(bodyOf(mail.getValue())).contains("<html", code.getValue(), "5분").doesNotContain("{{");
    }

    @Test
    void sendRejectsRegisteredEmail() {
        when(memberRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertError(() -> service.send(EMAIL), ErrorStatus.MEMBER_EMAIL_DUPLICATED);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendRejectsLockedEmail() {
        when(redisTemplate.hasKey("email-verification:locked:" + EMAIL)).thenReturn(true);

        assertError(() -> service.send(EMAIL), ErrorStatus.VERIFY_TOO_MANY_FAILURES);
    }

    @Test
    void sendRejectsWithinSixtySeconds() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertError(() -> service.send(EMAIL), ErrorStatus.VERIFY_RESEND_TOO_SOON);
    }

    @Test
    void sendFailureReleasesCooldownAndStoresNoCode() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new MailSendException("down")).when(mailSender).send(any(MimeMessage.class));

        assertError(() -> service.send(EMAIL), ErrorStatus.MAIL_SEND_FAILED);
        verify(redisTemplate).delete("email-verification:cooldown:" + EMAIL);
        verify(values, never()).set(eq("email-verification:code:" + EMAIL), anyString(), any(Duration.class));
    }

    @Test
    void confirmMarksEmailVerifiedForThirtyMinutes() {
        when(values.get("email-verification:code:" + EMAIL)).thenReturn("384021");
        when(values.increment("email-verification:failures:" + EMAIL)).thenReturn(1L);

        service.confirm(EMAIL, "384021");

        verify(values).set("email-verification:verified:" + EMAIL, "1", Duration.ofMinutes(30));
        verify(redisTemplate).delete("email-verification:code:" + EMAIL);
    }

    @Test
    void confirmCountsWrongCodeAndReturnsRemainingAttempts() {
        when(values.get("email-verification:code:" + EMAIL)).thenReturn("384021");
        when(values.increment("email-verification:failures:" + EMAIL)).thenReturn(2L);

        assertThatThrownBy(() -> service.confirm(EMAIL, "000000"))
                .isInstanceOf(GeneralException.class)
                .satisfies(error -> {
                    GeneralException exception = (GeneralException) error;
                    assertThat(exception.getCode()).isEqualTo(ErrorStatus.VERIFY_CODE_MISMATCH);
                    assertThat(exception.getResult())
                            .extracting("remainingAttempts")
                            .isEqualTo(3L);
                });
    }

    @Test
    void firstAttemptKeepsCountForOneHour() {
        when(values.get("email-verification:code:" + EMAIL)).thenReturn("384021");
        when(values.increment("email-verification:failures:" + EMAIL)).thenReturn(1L);

        assertError(() -> service.confirm(EMAIL, "000000"), ErrorStatus.VERIFY_CODE_MISMATCH);
        verify(redisTemplate).expire("email-verification:failures:" + EMAIL, Duration.ofHours(1));
    }

    @Test
    void confirmRejectsCorrectCodeBeyondFifthAttempt() {
        when(values.get("email-verification:code:" + EMAIL)).thenReturn("384021");
        when(values.increment("email-verification:failures:" + EMAIL)).thenReturn(6L);

        assertError(() -> service.confirm(EMAIL, "384021"), ErrorStatus.VERIFY_TOO_MANY_FAILURES);
        verify(values, never()).set(eq("email-verification:verified:" + EMAIL), anyString(), any(Duration.class));
    }

    @Test
    void confirmRejectsExpiredOrNeverSentCode() {
        when(values.get("email-verification:code:" + EMAIL)).thenReturn(null);

        assertError(() -> service.confirm(EMAIL, "384021"), ErrorStatus.VERIFY_CODE_EXPIRED);
    }

    @Test
    void confirmLocksForOneHourOnFifthFailure() {
        when(values.get("email-verification:code:" + EMAIL)).thenReturn("384021");
        when(values.increment("email-verification:failures:" + EMAIL)).thenReturn(5L);

        assertError(() -> service.confirm(EMAIL, "000000"), ErrorStatus.VERIFY_TOO_MANY_FAILURES);
        verify(values).set("email-verification:locked:" + EMAIL, "1", Duration.ofHours(1));
        verify(redisTemplate).delete("email-verification:code:" + EMAIL);
    }

    @Test
    void confirmRejectsWhileLocked() {
        when(redisTemplate.hasKey("email-verification:locked:" + EMAIL)).thenReturn(true);

        assertError(() -> service.confirm(EMAIL, "384021"), ErrorStatus.VERIFY_TOO_MANY_FAILURES);
    }

    @Test
    void checkVerifiedRejectsUnverifiedEmail() {
        when(redisTemplate.hasKey("email-verification:verified:" + EMAIL)).thenReturn(false);

        assertError(() -> service.checkVerified(EMAIL), ErrorStatus.VERIFY_REQUIRED);
    }

    private static String bodyOf(Part part) throws Exception {
        if (part.getContent() instanceof Multipart multipart) {
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                body.append(bodyOf(multipart.getBodyPart(i)));
            }
            return body.toString();
        }
        return part.getContent().toString();
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorStatus expected) {
        assertThatThrownBy(call)
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(expected);
    }
}
