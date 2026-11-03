package com.hyeja.userservice.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.userservice.domain.member.entity.Member;
import com.hyeja.userservice.global.security.JwtProvider;
import com.hyeja.userservice.global.security.TokenBlacklist;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String INTERNAL_TOKEN = "hyeja-test-only-internal-token-0123456789";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtProvider jwtProvider;

    // 테스트에는 Redis가 없으므로 로그아웃 블랙리스트 조회만 대신합니다(기본값 false).
    @MockitoBean
    private TokenBlacklist tokenBlacklist;

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
    void internalProfileRejectsUserJwtWithoutInternalToken() throws Exception {
        Member member = Member.builder()
                .email("hyeja@example.com")
                .password("encoded-password")
                .nickname("민지")
                .build();
        ReflectionTestUtils.setField(member, "memberId", 1L);

        mvc.perform(get("/internal/members/1/profile")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(member)))
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
