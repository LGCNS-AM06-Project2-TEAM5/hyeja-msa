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
    void internalProfileIsReachableWithoutToken() throws Exception {
        // 서비스 간 호출에는 사용자 JWT가 없으므로 401이 아니라 실제 처리 결과(없는 회원 404)가 나와야 합니다.
        mvc.perform(get("/internal/members/999/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_001"));
    }
}
