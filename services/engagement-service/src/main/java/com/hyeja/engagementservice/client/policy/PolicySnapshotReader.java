package com.hyeja.engagementservice.client.policy;

import com.hyeja.engagementservice.client.policy.dto.PolicyApiResponse;
import com.hyeja.engagementservice.client.policy.dto.PolicySnapshotDTO;
import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// F5 호출은 engPolicySnapshot 회로를 거칩니다. 회로가 열려 호출하지 않은 경우도 다른 장애와 같이 SERVER_002로 바꾸며,
// 기존 스냅샷으로 대신 등록하지 않습니다.
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicySnapshotReader {
    static final String CIRCUIT_BREAKER = "engPolicySnapshot";

    private final PolicyClient policyClient;
    private final JsonMapper jsonMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public PolicySnapshotDTO getPolicy(String policyId) {
        PolicyApiResponse<PolicySnapshotDTO> response;
        try {
            response = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER)
                    .executeSupplier(() -> policyClient.getPolicy(policyId));
        } catch (FeignException exception) {
            if (isPolicyNotFound(exception)) {
                throw new GeneralException(ErrorStatus.POLICY_NOT_FOUND);
            }
            log.warn("F5 정책 조회 실패. policyId={}, status={}", policyId, exception.status());
            throw unavailable();
        } catch (CallNotPermittedException exception) {
            log.warn("policy-service 회로가 열려 있어 F5 정책을 조회하지 않았습니다.");
            throw unavailable();
        }
        if (response == null || !Boolean.TRUE.equals(response.isSuccess())
                || !"SUCCESS_001".equals(response.code()) || response.result() == null) {
            throw unavailable();
        }
        PolicySnapshotDTO snapshot = response.result();
        if (!policyId.equals(snapshot.policyId()) || snapshot.policyName() == null
                || snapshot.policyName().isBlank() || snapshot.categories() == null
                || snapshot.categories().stream().anyMatch(java.util.Objects::isNull)
                || !Boolean.TRUE.equals(snapshot.activeYn()) || !Boolean.FALSE.equals(snapshot.deleted())) {
            throw unavailable();
        }
        return snapshot;
    }

    private boolean isPolicyNotFound(FeignException exception) {
        if (exception.status() != 404) return false;
        try {
            var body = jsonMapper.readTree(exception.contentUTF8());
            return body != null && body.path("isSuccess").isBoolean()
                    && !body.path("isSuccess").asBoolean()
                    && "POLICY_001".equals(body.path("code").asString());
        } catch (RuntimeException invalidBody) {
            return false;
        }
    }

    private GeneralException unavailable() {
        return new GeneralException(ErrorStatus.POLICY_SERVICE_UNAVAILABLE);
    }
}
