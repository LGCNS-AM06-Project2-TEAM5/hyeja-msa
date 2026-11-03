package com.hyeja.engagementservice.policy.event;

import com.hyeja.engagementservice.policy.service.PolicySnapshotUpdater;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** policy-updated를 받아 policy_snapshot을 고칩니다. 같은 eventId는 PolicySnapshotUpdater가 한 번만 반영합니다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class PolicyUpdatedListener {
    private final PolicySnapshotUpdater updater;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = PolicyEventKafkaConfig.TOPIC, groupId = PolicyEventKafkaConfig.GROUP_ID,
            containerFactory = "policyUpdatedListenerContainerFactory")
    public void onPolicyUpdated(String payload) {
        PolicyUpdatedEvent event = jsonMapper.readValue(payload, PolicyUpdatedEvent.class);
        if (updater.apply(event)) {
            log.info("policy_snapshot 반영. eventId={}, policyId={}, deleted={}", event.eventId(), event.policyId(), event.deleted());
        }
    }
}
