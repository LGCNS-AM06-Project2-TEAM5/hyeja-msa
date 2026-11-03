package com.hyeja.engagementservice.member.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DltNotificationService {

    private final JavaMailSender mailSender;
    private final Map<String, AtomicInteger> topicFailureCounters = new ConcurrentHashMap<>();

    @Value("${admin.email:admin-alerts@hyeja.com}")
    private String adminEmail;

    @Value("${admin.dlt-threshold:3}")
    private int dltThreshold;

    public void recordFailure(String topic, String key, String payload) {
        AtomicInteger counter = topicFailureCounters.computeIfAbsent(topic, ignored -> new AtomicInteger());
        int failureCount = counter.incrementAndGet();
        log.warn("DLT 메시지 누적. topic={}, key={}, count={}", topic, key, failureCount);

        if (failureCount < dltThreshold) {
            return;
        }

        if (sendAlert(topic, failureCount, key, payload)) {
            counter.set(0);
        }
    }

    private boolean sendAlert(String topic, int failureCount, String key, String payload) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject("[HYEJA MSA 경고] Kafka DLT 메시지 처리 실패 " + failureCount + "회 도달");
            message.setText("""
                    Kafka DLT(Dead Letter Topic)에 실패 메시지가 임계치에 도달했습니다.

                    - 대상 DLT 토픽: %s
                    - 누적 실패 건수: %d회
                    - 최근 메시지 키: %s
                    - 최근 실패 페이로드: %s

                    시스템 로그와 engagement-service Consumer 상태를 점검해 주세요.
                    """.formatted(topic, failureCount, key, payload));
            mailSender.send(message);
            log.info("DLT 관리자 이메일 발송 완료. topic={}, recipient={}", topic, adminEmail);
            return true;
        } catch (RuntimeException exception) {
            log.error("DLT 관리자 이메일 발송 실패. topic={}, recipient={}", topic, adminEmail, exception);
            return false;
        }
    }
}
