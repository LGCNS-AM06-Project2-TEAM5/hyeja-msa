package com.hyeja.policyservice.term.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.term.dto.TermResponseDTO;
import com.hyeja.policyservice.term.dto.TermDetailResponseDTO;
import com.hyeja.policyservice.term.service.TermService;
import com.hyeja.policyservice.global.exception.ExceptionAdvice;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TermControllerTest {

    private final TermService termService = mock(TermService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TermController(termService))
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Test
    void returnsTerms() throws Exception {
        when(termService.getTerms()).thenReturn(List.of(
                TermResponseDTO.builder()
                        .termId(1L)
                        .term("중위소득")
                        .easyDescription("전체 가구를 소득 순서로 세웠을 때 가운데 가구의 소득")
                        .example("중위소득 60% 이하")
                        .build(),
                TermResponseDTO.builder()
                        .termId(2L)
                        .term("무주택자")
                        .easyDescription("본인 명의 주택을 소유하지 않은 사람")
                        .build()
        ));

        mvc.perform(get("/api/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result[0].termId").value(1))
                .andExpect(jsonPath("$.result[0].term").value("중위소득"))
                .andExpect(jsonPath("$.result[0].easyDescription")
                        .value("전체 가구를 소득 순서로 세웠을 때 가운데 가구의 소득"))
                .andExpect(jsonPath("$.result[0].example").value("중위소득 60% 이하"))
                .andExpect(jsonPath("$.result[1].example").value(nullValue()));

        verify(termService).getTerms();
    }

    @Test
    void returnsTermDetail() throws Exception {
        when(termService.getTerm(1L)).thenReturn(
                new TermDetailResponseDTO(1L, "중위소득", "전체 가구 소득의 중간값"));

        mvc.perform(get("/api/terms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.termId").value(1))
                .andExpect(jsonPath("$.result.termName").value("중위소득"))
                .andExpect(jsonPath("$.result.description")
                        .value("전체 가구 소득의 중간값"));

        verify(termService).getTerm(1L);
    }
}
