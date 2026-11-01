package com.hyeja.policyservice.event;

import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Kafka 발행 구현이 들어오기 전까지 쓰는 빈 구현입니다. 아무것도 보내지 않습니다.
@Slf4j
@Component
public class NoOpPolicyChangedPublisher implements PolicyChangedPublisher {

    @Override
    public void publish(Collection<String> policyIds) {
        log.debug("policy-updated 발행 전(빈 구현): {}건", policyIds.size());
    }
}
