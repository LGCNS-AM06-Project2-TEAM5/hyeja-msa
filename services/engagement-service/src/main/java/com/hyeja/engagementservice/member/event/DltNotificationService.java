package com.hyeja.engagementservice.member.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DltNotificationService {
    private final JavaMailSender mailSender;
    private final DltAlertStateRepository stateRepository;
    @Value("${admin.email:admin-alerts@hyeja.com}")
    private String adminEmail;
    @Value("${admin.dlt-threshold:3}")
    private int dltThreshold;

    @Transactional
    public void recordFailure(String topic, String key, String payload) {
        DltAlertState state = stateRepository.findByTopicForUpdate(topic)
                .orElseGet(() -> new DltAlertState(topic));
        int failureCount = state.recordFailure(key, payload, dltThreshold);
        log.warn("DLT 메시지 누적. topic={}, key={}, count={}", topic, key, failureCount);
        if (state.isNotificationPending()) trySend(state);
        stateRepository.save(state);
    }

    @Scheduled(fixedDelayString = "${admin.dlt-retry-interval-ms:60000}")
    @Transactional
    public void retryPendingAlerts() {
        for (DltAlertState state : stateRepository.findAllByNotificationPendingTrue()) {
            trySend(state);
            stateRepository.save(state);
        }
    }

    private void trySend(DltAlertState state) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject("[HYEJA MSA 경고] Kafka DLT 메시지 처리 실패 " + state.getFailureCount() + "회 도달");
            message.setText("""
                    Kafka DLT(Dead Letter Topic)에 실패 메시지가 임계치에 도달했습니다.

                    - 대상 DLT 토픽: %s
                    - 누적 실패 건수: %d회
                    - 최근 메시지 키: %s
                    - 최근 실패 페이로드: %s

                    시스템 로그와 engagement-service Consumer 상태를 점검해 주세요.
                    """.formatted(state.getDltTopic(), state.getFailureCount(),
                    state.getLatestEventKey(), state.getLatestPayload()));
            mailSender.send(message);
            state.markNotified();
            log.info("DLT 관리자 이메일 발송 완료. topic={}, recipient={}", state.getDltTopic(), adminEmail);
        } catch (RuntimeException exception) {
            log.error("DLT 관리자 이메일 발송 실패. topic={}, recipient={}. 스케줄러가 재시도합니다.",
                    state.getDltTopic(), adminEmail, exception);
        }
    }
}
