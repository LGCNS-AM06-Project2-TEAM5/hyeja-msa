package com.hyeja.engagementservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hyeja.engagementservice.client.policy.PolicyClient;
import com.hyeja.engagementservice.client.policy.PolicySnapshotReader;
import com.hyeja.engagementservice.client.policy.dto.PolicyApiResponse;
import com.hyeja.engagementservice.client.policy.dto.PolicySnapshotDTO;
import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import com.hyeja.engagementservice.policy.enums.PolicyCategory;
import feign.Request;
import feign.RetryableException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PolicySnapshotReaderTest {
    private final PolicyClient client = mock(PolicyClient.class);
    private final PolicySnapshotReader reader = new PolicySnapshotReader(client, JsonMapper.builder().build());

    @Test
    void connectionRefusedIsUnavailableRatherThanPolicyNotFound() {
        Request request = Request.create(Request.HttpMethod.GET, "http://policy-service/internal/policies/P1",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(client.getPolicy("P1")).thenThrow(new RetryableException(-1, "connection refused",
                Request.HttpMethod.GET, new ConnectException("connection refused"), (Long) null, request));

        assertUnavailable();
    }

    @Test
    void wrongSuccessCodeIsNotAcceptedEvenWithValidSnapshot() {
        when(client.getPolicy("P1")).thenReturn(new PolicyApiResponse<>(true, "OTHER_CODE", "성공",
                new PolicySnapshotDTO("P1", "정책", Set.of(PolicyCategory.OTHER), null,
                        null, null, null, true, false)));

        assertUnavailable();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> reader.getPolicy("P1"))
                .isInstanceOfSatisfying(GeneralException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorStatus.POLICY_SERVICE_UNAVAILABLE));
    }
}
