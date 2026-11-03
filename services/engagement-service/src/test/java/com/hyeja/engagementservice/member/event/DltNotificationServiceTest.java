package com.hyeja.engagementservice.member.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class DltNotificationServiceTest {
    private JavaMailSender mailSender;
    private DltAlertStateRepository repository;
    private AtomicReference<DltAlertState> storedState;
    private DltNotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        repository = mock(DltAlertStateRepository.class);
        storedState = new AtomicReference<>();
        when(repository.findByTopicForUpdate("member-withdrawn-dlt"))
                .thenAnswer(ignored -> Optional.ofNullable(storedState.get()));
        when(repository.save(any(DltAlertState.class))).thenAnswer(invocation -> {
            DltAlertState state = invocation.getArgument(0);
            storedState.set(state);
            return state;
        });
        service = service(mailSender);
    }

    @Test
    void 재시작해도_누적_건수를_복원해_세_번째_DLT에서_메일을_보낸다() {
        service.recordFailure("member-withdrawn-dlt", "1", "first");
        service.recordFailure("member-withdrawn-dlt", "2", "second");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        DltNotificationService restartedService = service(mailSender);
        restartedService.recordFailure("member-withdrawn-dlt", "3", "third");

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertThat(storedState.get().getFailureCount()).isZero();
        assertThat(storedState.get().isNotificationPending()).isFalse();
    }

    @Test
    void 메일_발송_실패를_영속화하고_추가_DLT_없이_재시도한다() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        service.recordFailure("member-withdrawn-dlt", "1", "first");
        service.recordFailure("member-withdrawn-dlt", "2", "second");
        service.recordFailure("member-withdrawn-dlt", "3", "third");
        assertThat(storedState.get().isNotificationPending()).isTrue();
        assertThat(storedState.get().getFailureCount()).isEqualTo(3);

        reset(mailSender);
        when(repository.findAllByNotificationPendingTrue()).thenReturn(List.of(storedState.get()));
        service.retryPendingAlerts();

        verify(mailSender).send(any(SimpleMailMessage.class));
        assertThat(storedState.get().isNotificationPending()).isFalse();
        assertThat(storedState.get().getFailureCount()).isZero();
    }

    @Test
    void 관리자_이메일이_비어_있으면_SMTP_호출을_건너뛰고_대기_상태를_유지한다() {
        service = service(mailSender, "  ");
        service.recordFailure("member-withdrawn-dlt", "1", "first");
        service.recordFailure("member-withdrawn-dlt", "2", "second");
        service.recordFailure("member-withdrawn-dlt", "3", "third");

        when(repository.findAllByNotificationPendingTrue()).thenReturn(List.of(storedState.get()));
        service.retryPendingAlerts();

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertThat(storedState.get().isNotificationPending()).isTrue();
        assertThat(storedState.get().getFailureCount()).isEqualTo(3);
    }

    private DltNotificationService service(JavaMailSender sender) {
        return service(sender, "operator@example.com");
    }

    private DltNotificationService service(JavaMailSender sender, String adminEmail) {
        DltNotificationService notificationService = new DltNotificationService(sender, repository);
        ReflectionTestUtils.setField(notificationService, "adminEmail", adminEmail);
        ReflectionTestUtils.setField(notificationService, "dltThreshold", 3);
        return notificationService;
    }
}
