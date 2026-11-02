package com.hyeja.policyservice.policy.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.ExceptionAdvice;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.policy.service.PolicyService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PolicyControllerTest {
    private final PolicyService service = mock(PolicyService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PolicyController(service))
                .setControllerAdvice(new ExceptionAdvice()).build();
    }

    @Test
    void returnsOriginalSyncResponseForAdmin() throws Exception {
        when(service.fetchAndSaveHousingPolicies()).thenReturn(3);
        mvc.perform(post("/api/policies/sync")
                        .header("X-Member-Id", "1").header("X-Member-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result").value("온통청년 주거 정책 3건 동기화가 완료되었습니다."));
    }

    @Test
    void rejectsMissingOrInvalidIdentityBeforeCollecting() throws Exception {
        mvc.perform(post("/api/policies/sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
        for (String id : new String[]{"", "abc", "0", "-1", "9223372036854775808"}) {
            mvc.perform(post("/api/policies/sync")
                            .header("X-Member-Id", id).header("X-Member-Role", "ADMIN"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsNonAdminAndMissingRoleBeforeCollecting() throws Exception {
        mvc.perform(post("/api/policies/sync").header("X-Member-Id", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_004"));
        mvc.perform(post("/api/policies/sync")
                        .header("X-Member-Id", "1").header("X-Member-Role", "USER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void returnsOriginalStopCodeAndProgressWhenPageFails() throws Exception {
        when(service.fetchAndSaveHousingPolicies()).thenThrow(new GeneralException(
                ErrorStatus.POLICY_SYNC_STOPPED, Map.of("stoppedPage", 2, "savedCount", 4)));
        mvc.perform(post("/api/policies/sync")
                        .header("X-Member-Id", "1").header("X-Member-Role", "ADMIN"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("POLICY_002"))
                .andExpect(jsonPath("$.result.stoppedPage").value(2))
                .andExpect(jsonPath("$.result.savedCount").value(4));
    }
}
