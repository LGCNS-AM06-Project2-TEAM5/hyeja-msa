package com.hyeja.engagementservice.policy.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 관리자 대시보드가 Prometheus에서 읽는 policy-updated 처리 건수입니다.
 * listener 기본 지표(spring_kafka_listener_seconds)는 재시도마다 실패가 늘어 DLT 건수와 다르므로 따로 셉니다.
 * Prometheus 이름: hyeja_policy_updated_events_total{result="applied|duplicate"}, hyeja_policy_updated_dlt_total
 */
@Component
public class PolicyEventMetrics {
    public static final String EVENTS = "hyeja.policy.updated.events";
    public static final String DLT = "hyeja.policy.updated.dlt";

    private final Counter applied;
    private final Counter duplicate;
    private final Counter dlt;

    public PolicyEventMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry registry = registryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.applied = Counter.builder(EVENTS).tag("result", "applied")
                .description("policy_snapshot에 반영한 policy-updated 수").register(registry);
        this.duplicate = Counter.builder(EVENTS).tag("result", "duplicate")
                .description("이미 반영한 eventId라 건너뛴 policy-updated 수").register(registry);
        this.dlt = Counter.builder(DLT)
                .description("재시도 후에도 실패해 policy-updated-dlt로 보낸 메시지 수").register(registry);
    }

    public void recordApplied(boolean applied) {
        (applied ? this.applied : this.duplicate).increment();
    }

    public void recordDlt() {
        dlt.increment();
    }
}
