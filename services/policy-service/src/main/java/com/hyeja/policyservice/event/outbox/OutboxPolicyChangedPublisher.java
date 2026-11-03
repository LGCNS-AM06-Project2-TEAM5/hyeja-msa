package com.hyeja.policyservice.event.outbox;

import com.hyeja.policyservice.event.PolicyChangedPublisher;
import com.hyeja.policyservice.event.PolicyUpdatedEvent;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// policy-updated를 바로 Kafka로 보내지 않고 policy_outbox에 적어 둡니다(#1 결정 ⑤ Outbox).
// 정책을 저장하는 트랜잭션 안에서 부르면 저장과 함께 커밋·롤백되므로, 저장은 됐는데 이벤트가 빠지는 일이 없습니다.
// Kafka 전송은 커밋된 행을 읽어 따로 보냅니다.
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPolicyChangedPublisher implements PolicyChangedPublisher {

    private final PolicyRepository policyRepository;
    private final PolicyOutboxRepository policyOutboxRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    @Transactional
    public void publish(Collection<String> policyIds) {
        OffsetDateTime occurredAt = OffsetDateTime.now(clock);
        for (String policyId : new LinkedHashSet<>(policyIds)) {
            Policy policy = policyRepository.findById(policyId).orElse(null);
            if (policy == null) {
                log.warn("policy-updated 대상 정책이 없어 건너뜁니다. policyId={}", policyId);
                continue;
            }
            String eventId = UUID.randomUUID().toString();
            String payload = jsonMapper.writeValueAsString(PolicyUpdatedEvent.of(eventId, occurredAt, policy));
            policyOutboxRepository.save(new PolicyOutbox(eventId, policyId, PolicyUpdatedEvent.TYPE, payload,
                    occurredAt.toLocalDateTime()));
        }
    }
}
