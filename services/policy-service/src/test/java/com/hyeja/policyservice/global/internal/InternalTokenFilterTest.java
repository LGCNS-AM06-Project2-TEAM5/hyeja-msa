package com.hyeja.policyservice.global.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// /internal/**은 X-Internal-Token이 맞을 때만 컨트롤러까지 가고, /api/**는 영향이 없는지 봅니다.
// 없는 정책 ID로 부르므로 토큰이 맞으면 F5 컨트롤러의 404(POLICY_001)가 나옵니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalTokenFilterTest {

    @Autowired private MockMvc mvc;

    @Test
    void 토큰이_없으면_401() throws Exception {
        mvc.perform(get("/internal/policies/none"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void 토큰이_다르면_401() throws Exception {
        mvc.perform(get("/internal/policies/none").header(InternalToken.HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void 토큰이_맞으면_내부_API까지_간다() throws Exception {
        mvc.perform(get("/internal/policies/none").header(InternalToken.HEADER, "test-internal-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_001"));
    }

    @Test
    void 경로를_인코딩하거나_세미콜론을_붙여도_막는다() throws Exception {
        mvc.perform(get(URI.create("/%69nternal/policies/none")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(URI.create("/internal;x=1/policies/none")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 외부_API는_토큰을_보지_않는다() throws Exception {
        mvc.perform(get("/api/regions"))
                .andExpect(status().isOk());
    }
}
