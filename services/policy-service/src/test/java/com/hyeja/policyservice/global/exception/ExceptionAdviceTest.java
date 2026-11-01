package com.hyeja.policyservice.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ExceptionAdviceTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Test
    void 성공_응답은_공통_형식이다() throws Exception {
        mockMvc.perform(get("/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result").value("hello"));
    }

    @Test
    void GeneralException은_코드의_상태와_메시지로_응답한다() throws Exception {
        mockMvc.perform(get("/policy-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("POLICY_001"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 정책입니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 지원하지_않는_메서드는_COMMON_006이다() throws Exception {
        mockMvc.perform(post("/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_006"));
    }

    @Test
    void 처리하지_못한_예외는_SERVER_001이다() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SERVER_001"));
    }

    @RestController
    static class TestController {

        @GetMapping("/ok")
        ApiResponse<String> ok() {
            return ApiResponse.onSuccess("hello");
        }

        @GetMapping("/policy-not-found")
        ApiResponse<String> policyNotFound() {
            throw new GeneralException(ErrorStatus.POLICY_NOT_FOUND);
        }

        @GetMapping("/boom")
        ApiResponse<String> boom() {
            throw new IllegalStateException("예상하지 못한 오류");
        }
    }
}
