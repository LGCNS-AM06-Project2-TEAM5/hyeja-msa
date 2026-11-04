package com.hyeja.policyservice.event.outbox;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.global.auth.LoginMemberIdArgumentResolver;
import com.hyeja.policyservice.global.exception.ExceptionAdvice;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PolicyOutboxAdminControllerTest {

    private final PolicyOutboxStatusService service = mock(PolicyOutboxStatusService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PolicyOutboxAdminController(service))
            .setCustomArgumentResolvers(new LoginMemberIdArgumentResolver())
            .setControllerAdvice(new ExceptionAdvice())
            .build();

    @Test
    void 관리자는_outbox_상태를_받는다() throws Exception {
        LocalDateTime at = LocalDateTime.of(2026, 11, 3, 21, 5);
        given(service.getStatus()).willReturn(new PolicyOutboxStatusDTO(1, 2, at, List.of(
                new PolicyOutboxStatusDTO.RecentEvent("e-3", "p-3", "policy-updated", at, null))));

        mvc.perform(get("/api/policies/admin/events")
                        .header("X-Member-Id", "1").header("X-Member-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.pendingCount").value(1))
                .andExpect(jsonPath("$.result.publishedCount").value(2))
                .andExpect(jsonPath("$.result.lastPublishedAt").exists())
                .andExpect(jsonPath("$.result.recentEvents[0].eventId").value("e-3"))
                .andExpect(jsonPath("$.result.recentEvents[0].policyId").value("p-3"));
    }

    @Test
    void 일반_회원은_403을_받는다() throws Exception {
        mvc.perform(get("/api/policies/admin/events")
                        .header("X-Member-Id", "1").header("X-Member-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_004"));

        verify(service, never()).getStatus();
    }

    @Test
    void 회원_헤더가_없으면_401을_받는다() throws Exception {
        mvc.perform(get("/api/policies/admin/events").header("X-Member-Role", "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_002"));

        verify(service, never()).getStatus();
    }
}
