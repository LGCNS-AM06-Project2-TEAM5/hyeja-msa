package com.hyeja.policyservice.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.global.exception.ExceptionAdvice;
import com.hyeja.policyservice.internal.controller.PolicyCandidateController;
import com.hyeja.policyservice.internal.service.PolicyCandidateService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PolicyCandidateControllerTest {
    @Test
    void returnsServerErrorInsteadOfEmptySuccessWhenCandidateLookupFails() throws Exception {
        PolicyCandidateService service = mock(PolicyCandidateService.class);
        when(service.findCandidates(any())).thenThrow(new IllegalStateException("DB unavailable"));
        var mvc = MockMvcBuilders.standaloneSetup(new PolicyCandidateController(service))
                .setControllerAdvice(new ExceptionAdvice()).build();

        mvc.perform(post("/internal/policies/candidates").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories":[],"regionCode":"11440","today":"2030-01-02"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SERVER_001"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
                .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.nullValue()));
    }
}
