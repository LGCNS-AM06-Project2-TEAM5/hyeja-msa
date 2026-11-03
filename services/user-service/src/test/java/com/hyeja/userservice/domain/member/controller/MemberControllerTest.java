package com.hyeja.userservice.domain.member.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.userservice.domain.member.dto.MemberAccountResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberFindEmailResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberLoginResponseDTO;
import com.hyeja.userservice.domain.member.service.MemberService;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.ExceptionAdvice;
import com.hyeja.userservice.global.exception.GeneralException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MemberControllerTest {

    private static final String SIGNUP_BODY = """
            {
              "email": "hyeja@example.com",
              "password": "hyeja1234!",
              "nickname": "민지",
              "profile": {
                "birth": "2000-03-15",
                "regionCode": "11440",
                "employmentCode": "EMPLOYED",
                "houselessYn": true
              }
            }
            """;

    private final MemberService memberService = mock(MemberService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MemberController(memberService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new ExceptionAdvice())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, "access-token", List.of())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signsUpMemberAndProfile() throws Exception {
        when(memberService.signup(any())).thenReturn(MemberAccountResponseDTO.builder()
                .memberId(1L)
                .email("hyeja@example.com")
                .nickname("민지")
                .createdAt(LocalDateTime.of(2026, 11, 3, 10, 0))
                .build());

        mvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result.memberId").value(1))
                .andExpect(jsonPath("$.result.email").value("hyeja@example.com"))
                .andExpect(jsonPath("$.result.nickname").value("민지"));
    }

    @Test
    void rejectsSignupWithoutProfile() throws Exception {
        mvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "hyeja@example.com",
                                  "password": "hyeja1234!",
                                  "nickname": "민지"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.result.profile").exists());
        verifyNoInteractions(memberService);
    }

    @Test
    void rejectsSignupWithInvalidNestedProfile() throws Exception {
        mvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "hyeja@example.com",
                                  "password": "hyeja1234!",
                                  "nickname": "민지",
                                  "profile": {
                                    "birth": "2099-01-01",
                                    "regionCode": "",
                                    "employmentCode": "EMPLOYED",
                                    "houselessYn": true
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.result['profile.birth']").exists())
                .andExpect(jsonPath("$.result['profile.regionCode']").exists());
        verifyNoInteractions(memberService);
    }

    @Test
    void returnsConflictWhenSignupEmailIsDuplicated() throws Exception {
        when(memberService.signup(any()))
                .thenThrow(new GeneralException(ErrorStatus.MEMBER_EMAIL_DUPLICATED));

        mvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SIGNUP_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_002"));
    }

    @Test
    void logsIn() throws Exception {
        when(memberService.login(any())).thenReturn(MemberLoginResponseDTO.builder()
                .accessToken("access-token")
                .memberId(1L)
                .nickname("민지")
                .build());

        mvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hyeja@example.com\",\"password\":\"hyeja1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result.accessToken").value("access-token"))
                .andExpect(jsonPath("$.result.memberId").value(1));
    }

    @Test
    void rejectsLoginWithoutPassword() throws Exception {
        mvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hyeja@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.result.password").exists());
        verifyNoInteractions(memberService);
    }

    @Test
    void returnsUnauthorizedWhenLoginFails() throws Exception {
        when(memberService.login(any())).thenThrow(new GeneralException(ErrorStatus.MEMBER_LOGIN_FAILED));

        mvc.perform(post("/api/members/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hyeja@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER_005"));
    }

    @Test
    void logsOutWithAuthenticatedToken() throws Exception {
        mvc.perform(post("/api/members/logout")
                        .principal(new UsernamePasswordAuthenticationToken(1L, "access-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(nullValue()));

        verify(memberService).logout("access-token");
    }

    @Test
    void returnsMyAccount() throws Exception {
        when(memberService.getMyAccount(1L)).thenReturn(MemberAccountResponseDTO.builder()
                .memberId(1L)
                .email("hyeja@example.com")
                .nickname("민지")
                .createdAt(LocalDateTime.of(2026, 11, 2, 16, 30))
                .build());

        mvc.perform(get("/api/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.memberId").value(1))
                .andExpect(jsonPath("$.result.email").value("hyeja@example.com"));
    }

    @Test
    void findsEmail() throws Exception {
        when(memberService.findEmail("민지", LocalDate.of(2000, 3, 15)))
                .thenReturn(MemberFindEmailResponseDTO.builder()
                        .email("hye***@example.com")
                        .joinedAt(LocalDate.of(2026, 9, 20))
                        .build());

        mvc.perform(get("/api/members/find-email")
                        .param("nickname", "민지")
                        .param("birth", "2000-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email").value("hye***@example.com"))
                .andExpect(jsonPath("$.result.joinedAt").value("2026-09-20"));
    }

    @Test
    void rejectsMissingBirth() throws Exception {
        mvc.perform(get("/api/members/find-email").param("nickname", "민지"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        verifyNoInteractions(memberService);
    }
}
