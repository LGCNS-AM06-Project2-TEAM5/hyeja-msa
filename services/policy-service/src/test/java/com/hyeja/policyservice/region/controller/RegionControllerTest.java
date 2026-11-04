package com.hyeja.policyservice.region.controller;

import com.hyeja.policyservice.region.dto.RegionResponseDTO.SidoDTO;
import com.hyeja.policyservice.region.dto.RegionResponseDTO.SigunguDTO;
import com.hyeja.policyservice.region.service.RegionService;
import com.hyeja.policyservice.global.exception.ExceptionAdvice;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegionControllerTest {

    private final RegionService regionService = mock(RegionService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RegionController(regionService))
                .setControllerAdvice(new ExceptionAdvice()).build();
    }

    // 명세의 응답 모양(sidoCode·sidoName·sigungu[regionCode·sigunguName])대로 내려가는지 확인합니다.
    @Test
    void returnsRegionsGroupedBySido() throws Exception {
        when(regionService.getRegions()).thenReturn(List.of(SidoDTO.builder()
                .sidoCode("11")
                .sidoName("서울특별시")
                .sigungu(List.of(SigunguDTO.builder().regionCode("11440").sigunguName("마포구").build()))
                .build()));

        mvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result[0].sidoCode").value("11"))
                .andExpect(jsonPath("$.result[0].sidoName").value("서울특별시"))
                .andExpect(jsonPath("$.result[0].sigungu[0].regionCode").value("11440"))
                .andExpect(jsonPath("$.result[0].sigungu[0].sigunguName").value("마포구"));
    }
}
