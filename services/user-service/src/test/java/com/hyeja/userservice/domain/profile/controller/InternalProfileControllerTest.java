package com.hyeja.userservice.domain.profile.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.userservice.domain.profile.dto.MemberProfileDTO;
import com.hyeja.userservice.domain.profile.enums.EmploymentStatus;
import com.hyeja.userservice.domain.profile.service.ProfileService;
import com.hyeja.userservice.domain.region.dto.RegionDTO;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.ExceptionAdvice;
import com.hyeja.userservice.global.exception.GeneralException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalProfileControllerTest {

    private final ProfileService profileService = mock(ProfileService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalProfileController(profileService))
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("회원 조건을 ApiResponse로 감싸 돌려준다")
    void getMemberProfile() throws Exception {
        when(profileService.getMemberProfile(1L)).thenReturn(MemberProfileDTO.builder()
                .memberId(1L)
                .birth(LocalDate.of(2000, 3, 15))
                .region(new RegionDTO("11440", "서울특별시 마포구"))
                .employmentCode(EmploymentStatus.EMPLOYED)
                .houselessYn(true)
                .build());

        mvc.perform(get("/internal/members/1/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result.birth").value("2000-03-15"))
                .andExpect(jsonPath("$.result.region.regionCode").value("11440"))
                .andExpect(jsonPath("$.result.region.sigunguName").value("서울특별시 마포구"))
                .andExpect(jsonPath("$.result.employmentCode").value("EMPLOYED"));
    }

    @Test
    @DisplayName("조건이 없으면 404 PROFILE_001, memberId가 숫자가 아니면 400 COMMON_001")
    void errors() throws Exception {
        when(profileService.getMemberProfile(1L))
                .thenThrow(new GeneralException(ErrorStatus.PROFILE_NOT_FOUND));

        mvc.perform(get("/internal/members/1/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_001"));
        mvc.perform(get("/internal/members/abc/profile"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }
}
