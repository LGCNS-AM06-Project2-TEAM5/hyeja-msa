package com.hyeja.policyservice.event.outbox;

import com.hyeja.policyservice.event.PolicyUpdatedEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

// 커밋된 policy_outbox 행을 적은 순서대로 policy-updated 토픽에 보냅니다. key는 policyId입니다.
// 보낸 뒤 published_at을 채우기 전에 멈추면 같은 이벤트가 다시 나갈 수 있어(최소 한 번 전달) engagement가 eventId로 거릅니다.
// 한 행이라도 실패하면 거기서 멈추고 다음 주기에 그 행부터 다시 보내, 같은 정책의 이벤트 순서가 뒤바뀌지 않게 합니다.
// Kafka 주소(spring.kafka.bootstrap-servers)가 있는 환경(Compose)에서만 PolicyOutboxRelayConfig가 만듭니다.
@Slf4j
@RequiredArgsConstructor
public class PolicyOutboxRelay {

    static final String TOPIC = PolicyUpdatedEvent.TYPE;
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final PolicyOutboxRepository policyOutboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    @Scheduled(initialDelayString = "${policy.outbox.relay-interval-ms:1000}",
            fixedDelayString = "${policy.outbox.relay-interval-ms:1000}")
    public void relayScheduled() {
        relayPending();
    }

    public int relayPending() {
        int sent = 0;
        for (PolicyOutbox row : policyOutboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()) {
            try {
                kafkaTemplate.send(TOPIC, row.getAggregateId(), row.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return sent;
            } catch (Exception e) {
                log.warn("policy-updated 전송 실패, 다음 주기에 다시 보냅니다. eventId={}, policyId={}",
                        row.getEventId(), row.getAggregateId(), e);
                return sent;
            }
            row.markPublished(LocalDateTime.now(clock));
            policyOutboxRepository.save(row);
            sent++;
        }
        if (sent > 0) {
            log.info("policy-updated {}건 전송", sent);
        }
        return sent;
    }
}
