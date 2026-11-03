package com.hyeja.policyservice.internal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.global.exception.ExceptionAdvice;
import com.hyeja.policyservice.internal.controller.PolicySnapshotController;
import com.hyeja.policyservice.internal.service.PolicySnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PolicySnapshotControllerTest {
    @Test
    void returnsServerErrorInsteadOfMissingPolicyOrSuccessfulEmptySnapshot() throws Exception {
        PolicySnapshotService service = mock(PolicySnapshotService.class);
        when(service.getSnapshot("policy")).thenThrow(new IllegalStateException("DB unavailable"));
        var mvc = MockMvcBuilders.standaloneSetup(new PolicySnapshotController(service))
                .setControllerAdvice(new ExceptionAdvice()).build();

        mvc.perform(get("/internal/policies/policy"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SERVER_001"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }
}
