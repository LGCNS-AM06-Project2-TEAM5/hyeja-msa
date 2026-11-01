package com.hyeja.policyservice.global.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.global.exception.ExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class LoginMemberIdArgumentResolverTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setCustomArgumentResolvers(new LoginMemberIdArgumentResolver())
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Test
    void 헤더의_회원_ID를_받는다() throws Exception {
        mockMvc.perform(get("/required").header(MemberHeaders.MEMBER_ID, "42"))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void 로그인이_필요한데_헤더가_없으면_401이다() throws Exception {
        mockMvc.perform(get("/required"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void 로그인이_선택이면_헤더가_없을_때_null이다() throws Exception {
        mockMvc.perform(get("/optional"))
                .andExpect(status().isOk())
                .andExpect(content().string("guest"));
    }

    @Test
    void 숫자가_아니거나_0_이하이면_401이다() throws Exception {
        for (String value : new String[] {"abc", "0", "-1"}) {
            mockMvc.perform(get("/optional").header(MemberHeaders.MEMBER_ID, value))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON_002"));
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/required")
        String required(@LoginMemberId Long memberId) {
            return String.valueOf(memberId);
        }

        @GetMapping("/optional")
        String optional(@LoginMemberId(required = false) Long memberId) {
            return memberId == null ? "guest" : String.valueOf(memberId);
        }
    }
}
