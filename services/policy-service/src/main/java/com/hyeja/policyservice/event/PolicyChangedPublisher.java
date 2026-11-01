package com.hyeja.policyservice.event;

import java.util.Collection;

// 정책이 저장·변경된 뒤 policy-updated 이벤트를 내보내는 지점입니다(#9).
// 수집 코드는 저장을 마친 정책 ID로 publish만 부르고, 발행 방식(Kafka·Outbox)은 이 구현이 정합니다.
public interface PolicyChangedPublisher {

    void publish(Collection<String> policyIds);
}
