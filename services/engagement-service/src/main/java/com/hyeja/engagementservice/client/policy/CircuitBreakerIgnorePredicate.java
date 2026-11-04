package com.hyeja.engagementservice.client.policy;

import feign.FeignException;
import java.util.function.Predicate;

// F5 Circuit Breaker가 실패율에서 빼는 예외입니다(application.yml resilience4j ignore-exception-predicate).
// 4xx(POLICY_001 404 포함)는 policy-service 장애가 아니므로 회로를 열지 않습니다.
// 제외해도 예외는 그대로 PolicySnapshotReader로 전달되어 기존처럼 처리됩니다.
// 5xx·연결 실패·Timeout·역직렬화 실패는 실패로 집계합니다.
public class CircuitBreakerIgnorePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof FeignException exception
                && exception.status() >= 400 && exception.status() < 500;
    }
}
