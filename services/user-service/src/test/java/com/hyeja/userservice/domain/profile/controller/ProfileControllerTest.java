package com.hyeja.userservice.domain.profile.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.userservice.domain.profile.dto.ProfileResponseDTO;
import com.hyeja.userservice.domain.profile.enums.EmploymentStatus;
import com.hyeja.userservice.domain.profile.service.ProfileService;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.ExceptionAdvice;
import com.hyeja.userservice.global.exception.GeneralException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileControllerTest {

    private static final String UPDATE_BODY = """
            {
              "birth": "2000-03-15",
              "regionCode": "11440",
              "employmentCode": "EMPLOYED",
              "houselessYn": true
            }
            """;

    private final ProfileService profileService = mock(ProfileService.class);
    private MockMvc mvc;

    // 토큰 검증을 통과한 회원 1번이 요청한 상황을 만듭니다.
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService))
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

    private ProfileResponseDTO response() {
        return ProfileResponseDTO.builder()
                .birth(LocalDate.of(2000, 3, 15))
                .age(26)
                .regionCode("11440")
                .regionName("서울특별시 마포구")
                .employmentCode(EmploymentStatus.EMPLOYED)
                .employmentName("재직자")
                .houselessYn(true)
                .build();
    }

    @Test
    @DisplayName("토큰의 회원 ID로 내 조건을 조회한다")
    void getMyProfile() throws Exception {
        when(profileService.getMyProfile(1L)).thenReturn(response());

        mvc.perform(get("/api/members/me/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result.age").value(26))
                .andExpect(jsonPath("$.result.regionName").value("서울특별시 마포구"))
                .andExpect(jsonPath("$.result.employmentName").value("재직자"))
                .andExpect(jsonPath("$.result.marriageCode").doesNotExist());
    }

    @Test
    @DisplayName("내 조건을 수정하고 조회와 같은 형식으로 돌려준다")
    void updateMyProfile() throws Exception {
        when(profileService.updateMyProfile(eq(1L), any())).thenReturn(response());

        mvc.perform(patch("/api/members/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result.regionCode").value("11440"));
    }

    @Test
    @DisplayName("미래 생년월일이나 빈 지역 코드면 400 COMMON_003을 돌려주고 서비스를 호출하지 않는다")
    void rejectsInvalidRequest() throws Exception {
        mvc.perform(patch("/api/members/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "birth": "2099-01-01",
                                  "regionCode": "",
                                  "employmentCode": "EMPLOYED",
                                  "houselessYn": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.result.birth").exists())
                .andExpect(jsonPath("$.result.regionCode").exists());
        verifyNoInteractions(profileService);
    }

    @Test
    @DisplayName("조건이 없으면 404 PROFILE_001, 지역 코드가 없으면 404 REGION_001")
    void errors() throws Exception {
        when(profileService.getMyProfile(1L))
                .thenThrow(new GeneralException(ErrorStatus.PROFILE_NOT_FOUND));
        when(profileService.updateMyProfile(eq(1L), any()))
                .thenThrow(new GeneralException(ErrorStatus.REGION_NOT_FOUND));

        mvc.perform(get("/api/members/me/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_001"));
        mvc.perform(patch("/api/members/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGION_001"));
    }
}
