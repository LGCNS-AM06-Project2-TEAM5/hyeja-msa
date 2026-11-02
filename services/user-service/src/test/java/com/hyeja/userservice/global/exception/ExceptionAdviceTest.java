package com.hyeja.userservice.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.userservice.global.apiPayload.ApiResponse;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class ExceptionAdviceTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Test
    void successHasCommonEnvelope() throws Exception {
        mvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.message").value("성공입니다."))
                .andExpect(jsonPath("$.result.id").value(1));
    }

    @Test
    void businessExceptionUsesDeclaredStatus() throws Exception {
        mvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_005"))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    @Test
    void validationIncludesFieldMessage() throws Exception {
        mvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.result.email").value("이메일 형식이 올바르지 않습니다."));
    }

    @Test
    void malformedJsonHasCommonEnvelope() throws Exception {
        mvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void invalidParameterHasCommonEnvelope() throws Exception {
        mvc.perform(get("/test/parameter").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void methodNotAllowedPreservesAllowHeader() throws Exception {
        mvc.perform(post("/test/success"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("COMMON_006"));
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() throws Exception {
        mvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"isSuccess":false,"code":"SERVER_001",
                         "message":"서버 오류가 발생했습니다.","result":null}
                        """));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/success")
        ApiResponse<Map<String, Integer>> success() {
            return ApiResponse.onSuccess(Map.of("id", 1));
        }

        @GetMapping("/test/business")
        void business() {
            throw new GeneralException(ErrorStatus.NOT_FOUND);
        }

        @PostMapping("/test/validated")
        ApiResponse<EmailRequest> validated(@Valid @RequestBody EmailRequest request) {
            return ApiResponse.onSuccess(request);
        }

        @GetMapping("/test/parameter")
        ApiResponse<Integer> parameter(@RequestParam("page") int page) {
            return ApiResponse.onSuccess(page);
        }

        @GetMapping("/test/failure")
        void failure() {
            throw new IllegalStateException("internal secret detail");
        }
    }

    record EmailRequest(@NotBlank @Email(message = "이메일 형식이 올바르지 않습니다.") String email) {
    }
}
