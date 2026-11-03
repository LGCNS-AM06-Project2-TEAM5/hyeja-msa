package com.hyeja.engagementservice.member.event;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class DltNotificationServiceTest {

    private JavaMailSender mailSender;
    private DltNotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        service = new DltNotificationService(mailSender);
        ReflectionTestUtils.setField(service, "adminEmail", "operator@example.com");
        ReflectionTestUtils.setField(service, "dltThreshold", 3);
    }

    @Test
    void 세_번째_DLT에서_메일을_보내고_카운터를_초기화한다() {
        service.recordFailure("member-withdrawn-dlt", "1", "first");
        service.recordFailure("member-withdrawn-dlt", "2", "second");
        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        service.recordFailure("member-withdrawn-dlt", "3", "third");
        verify(mailSender, times(1)).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        service.recordFailure("member-withdrawn-dlt", "4", "fourth");
        verify(mailSender, times(1)).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }
}
