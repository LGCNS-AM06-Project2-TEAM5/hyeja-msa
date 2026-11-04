package com.hyeja.engagementservice.client.policy;

import com.hyeja.engagementservice.client.policy.dto.PolicyApiResponse;
import com.hyeja.engagementservice.client.policy.dto.PolicySnapshotDTO;
import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicySnapshotReader {
    private final PolicyClient policyClient;
    private final JsonMapper jsonMapper;

    public PolicySnapshotDTO getPolicy(String policyId) {
        PolicyApiResponse<PolicySnapshotDTO> response;
        try {
            response = policyClient.getPolicy(policyId);
        } catch (FeignException exception) {
            if (isPolicyNotFound(exception)) {
                throw new GeneralException(ErrorStatus.POLICY_NOT_FOUND);
            }
            log.warn("F5 정책 조회 실패. policyId={}, status={}", policyId, exception.status());
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
