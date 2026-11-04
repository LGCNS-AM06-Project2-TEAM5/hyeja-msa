package com.hyeja.recommendationservice.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.recommendationservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.recommendationservice.global.exception.GeneralException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// application.yml의 회로 설정이 F1·F2·F3 인스턴스마다 그대로 적용되고, 예외 분류가 의도대로인지 확인합니다.
@SpringBootTest
@ActiveProfiles("test")
class CircuitBreakerConfigurationTest {

    private static final List<String> INSTANCES =
            List.of("recUserProfile", "recPolicyCandidates", "recEngagementFavorites");

    @Autowired CircuitBreakerRegistry registry;

    @Test
    void 호출마다_독립_회로가_같은_초기_설정으로_만들어진다() {
        assertThat(registry.getAllCircuitBreakers()).extracting(CircuitBreaker::getName)
                .containsAll(INSTANCES);
        assertThat(INSTANCES.stream().map(registry::circuitBreaker).distinct()).hasSize(3);

        for (String name : INSTANCES) {
            CircuitBreakerConfig config = registry.circuitBreaker(name).getCircuitBreakerConfig();
            assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
            assertThat(config.getSlidingWindowSize()).isEqualTo(20);
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
            assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
            assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(Duration.ofSeconds(15).toMillis());
            assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
            assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isFalse();
            assertThat(config.getIgnoreExceptionPredicate()).isInstanceOf(CircuitBreakerIgnorePredicate.class);
        }
    }

    @Test
    void 비즈니스_오류와_4xx만_제외하고_장애는_실패로_센다() {
        CircuitBreakerIgnorePredicate ignore = new CircuitBreakerIgnorePredicate();

        assertThat(ignore.test(new GeneralException(ErrorStatus.MEMBER_NOT_FOUND))).isTrue();
        assertThat(ignore.test(new GeneralException(ErrorStatus.PROFILE_NOT_FOUND))).isTrue();
        assertThat(ignore.test(feignError(400))).isTrue();
        assertThat(ignore.test(feignError(401))).isTrue();
        assertThat(ignore.test(feignError(404))).isTrue();

        assertThat(ignore.test(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR))).isFalse();
        assertThat(ignore.test(feignError(500))).isFalse();
        assertThat(ignore.test(feignError(503))).isFalse();
        assertThat(ignore.test(new RetryableException(-1, "Connection refused", Request.HttpMethod.GET,
                (Long) null, request()))).isFalse();
        assertThat(ignore.test(new IllegalStateException("x"))).isFalse();
    }

    private static FeignException feignError(int status) {
        return FeignException.errorStatus("client#call()", Response.builder().status(status).request(request())
                .headers(Map.of()).body("{}", StandardCharsets.UTF_8).build());
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "/internal", Map.of(), null, StandardCharsets.UTF_8, null);
    }
}
