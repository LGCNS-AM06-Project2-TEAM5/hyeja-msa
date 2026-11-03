package com.hyeja.userservice.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String INTERNAL_TOKEN = "hyeja-test-only-internal-token-0123456789";

    @Autowired
    private MockMvc mvc;

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/health-check"))
                .andExpect(status().isOk())
                .andExpect(content().string("user-service OK"));
    }

    @Test
    void myAccountRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void internalProfileRejectsMissingToken() throws Exception {
        mvc.perform(get("/internal/members/999/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void internalProfileRejectsWrongToken() throws Exception {
        mvc.perform(get("/internal/members/999/profile").header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void internalProfileAcceptsValidToken() throws Exception {
        // 인증을 통과하면 실제 처리 결과(없는 회원 404)가 나와야 합니다.
        mvc.perform(get("/internal/members/999/profile").header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_001"));
    }
}
