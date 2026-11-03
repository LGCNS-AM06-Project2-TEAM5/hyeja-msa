package com.hyeja.recommendationservice.global.config;

import com.hyeja.recommendationservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.recommendationservice.global.exception.GeneralException;
import feign.FeignException;
import java.util.function.Predicate;

// F1·F2·F3 Circuit Breaker가 실패율에서 빼는 예외입니다(application.yml resilience4j ignore-exception-predicate).
// 4xx와 UserServiceErrorDecoder가 바꾼 MEMBER_001·PROFILE_001은 의존 서비스 장애가 아니므로 회로를 열지 않습니다.
// 제외해도 예외는 그대로 호출자에게 전달됩니다. 5xx·연결 실패·Timeout·역직렬화 실패는 실패로 집계합니다.
public class CircuitBreakerIgnorePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof GeneralException exception) {
            return exception.getCode() == ErrorStatus.MEMBER_NOT_FOUND
                    || exception.getCode() == ErrorStatus.PROFILE_NOT_FOUND;
        }
        return throwable instanceof FeignException exception
                && exception.status() >= 400 && exception.status() < 500;
    }
}
