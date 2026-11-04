package com.hyeja.engagementservice.client.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hyeja.engagementservice.client.policy.dto.PolicyApiResponse;
import com.hyeja.engagementservice.client.policy.dto.PolicySnapshotDTO;
import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import com.hyeja.engagementservice.policy.enums.PolicyCategory;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.DecodeException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

// F5 engPolicySnapshot 회로의 상태 전환과 예외 분류를 실제 PolicySnapshotReader로 확인합니다.
// 운영 설정(application.yml)과 같고 OPEN 대기 시간만 짧게 둡니다. 운영 설정 값 자체는 통합 테스트에서 확인합니다.
class PolicySnapshotCircuitBreakerTest {

    private final PolicyClient client = mock(PolicyClient.class);
    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(false)
            .ignoreException(new CircuitBreakerIgnorePredicate())
            .build());
    private final PolicySnapshotReader reader =
            new PolicySnapshotReader(client, JsonMapper.builder().build(), registry);

    @Test
    void 정상_응답은_그대로_돌려주고_성공으로_센다() {
        given(client.getPolicy("P1")).willReturn(ok());

        assertThat(reader.getPolicy("P1").policyName()).isEqualTo("정책");
        assertThat(breaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void 서버_오류가_반복되면_열리고_열린_동안은_부르지_않고_SERVER_002로_바꾼다() {
        given(client.getPolicy("P1")).willThrow(error(500, "SERVER_001"));
        for (int i = 0; i < 10; i++) {
            assertUnavailable();
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        willReturn(ok()).given(client).getPolicy("P1");
        assertUnavailable();
        assertUnavailable();
        verify(client, times(10)).getPolicy("P1");
        assertThat(breaker().getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(2);
    }

    @Test
    void 처음부터_열려_있으면_Feign을_부르지_않는다() {
        breaker().transitionToOpenState();

        assertUnavailable();

        verifyNoInteractions(client);
    }

    @Test
    void 대기_뒤_시험_호출이_실패하면_다시_열리고_성공하면_닫힌다() throws InterruptedException {
        given(client.getPolicy("P1")).willThrow(error(503, "SERVER_001"));
        for (int i = 0; i < 10; i++) {
            assertUnavailable();
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 시험 호출이 실패하면 다시 OPEN
        Thread.sleep(150);
        for (int i = 0; i < 3; i++) {
            assertUnavailable();
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(client, times(13)).getPolicy("P1");

        // 시험 호출이 성공하면 CLOSED
        Thread.sleep(150);
        willReturn(ok()).given(client).getPolicy("P1");
        reader.getPolicy("P1");
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        reader.getPolicy("P1");
        reader.getPolicy("P1");
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void POLICY_001은_그대로_돌려주고_회로를_열지_않는다() {
        given(client.getPolicy("P1")).willThrow(error(404, "POLICY_001"));
        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> reader.getPolicy("P1"))
                    .isInstanceOfSatisfying(GeneralException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorStatus.POLICY_NOT_FOUND));
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker().getMetrics().getNumberOfBufferedCalls()).isZero();
        verify(client, times(20)).getPolicy("P1");
    }

    // 4xx는 기존과 같이 SERVER_002로 응답하되(POLICY_001 404만 예외) 실패율에서는 뺍니다.
    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409})
    void 그_밖의_4xx는_기존대로_SERVER_002이고_실패율에서_뺀다(int status) {
        given(client.getPolicy("P1")).willThrow(error(status, "COMMON_002"));
        for (int i = 0; i < 20; i++) {
            assertUnavailable();
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker().getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void 연결_실패_Timeout_역직렬화_실패는_실패로_센다() {
        Request request = request();
        given(client.getPolicy("P1"))
                .willThrow(new RetryableException(-1, "connection refused", Request.HttpMethod.GET,
                        new ConnectException("connection refused"), (Long) null, request))
                .willThrow(new RetryableException(-1, "Read timed out", Request.HttpMethod.GET,
                        new SocketTimeoutException("Read timed out"), (Long) null, request))
                .willThrow(new DecodeException(200, "not json", request));

        for (int i = 0; i < 3; i++) {
            assertUnavailable();
        }

        assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> reader.getPolicy("P1"))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorStatus.POLICY_SERVICE_UNAVAILABLE));
    }

    private CircuitBreaker breaker() {
        return registry.circuitBreaker("engPolicySnapshot");
    }

    private static PolicyApiResponse<PolicySnapshotDTO> ok() {
        return new PolicyApiResponse<>(true, "SUCCESS_001", "성공",
                new PolicySnapshotDTO("P1", "정책", Set.of(PolicyCategory.OTHER), null,
                        null, null, null, true, false));
    }

    private static FeignException error(int status, String code) {
        String body = "{\"isSuccess\":false,\"code\":\"" + code + "\",\"message\":\"x\",\"result\":null}";
        return FeignException.errorStatus("PolicyClient#getPolicy(String)", Response.builder()
                .status(status)
                .request(request())
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build());
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "http://policy-service/internal/policies/P1",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }
}
