package com.hyeja.userservice.domain.member.controller;

import com.hyeja.userservice.domain.member.dto.EmailVerificationResponseDTO;
import com.hyeja.userservice.domain.member.service.EmailVerificationService;
import com.hyeja.userservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.userservice.global.exception.ExceptionAdvice;
import com.hyeja.userservice.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailVerificationControllerTest {

    private final EmailVerificationService service = mock(EmailVerificationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EmailVerificationController(service))
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Test
    void sendsCode() throws Exception {
        when(service.send("hyeja@example.com")).thenReturn(300L);

        mvc.perform(json("/api/members/email-verifications", "{\"email\": \"hyeja@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result.expiresInSeconds").value(300));
    }

    @Test
    void rejectsMalformedEmail() throws Exception {
        mvc.perform(json("/api/members/email-verifications", "{\"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.result.email").value("이메일 형식이 아니에요."));
        verifyNoInteractions(service);
    }

    @Test
    void returnsTooManyRequestsWhenResentTooSoon() throws Exception {
        when(service.send("hyeja@example.com"))
                .thenThrow(new GeneralException(ErrorStatus.VERIFY_RESEND_TOO_SOON));

        mvc.perform(json("/api/members/email-verifications", "{\"email\": \"hyeja@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFY_004"));
    }

    @Test
    void confirmsCode() throws Exception {
        mvc.perform(json(
                        "/api/members/email-verifications/confirmation",
                        "{\"email\": \"hyeja@example.com\", \"code\": \"384021\"}"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result.verified").value(true));
        verify(service).confirm("hyeja@example.com", "384021");
    }

    @Test
    void returnsBadRequestWithRemainingAttemptsWhenCodeMismatches() throws Exception {
        doThrow(new GeneralException(
                ErrorStatus.VERIFY_CODE_MISMATCH,
                new EmailVerificationResponseDTO.MismatchDTO(3)
        )).when(service).confirm("hyeja@example.com", "000000");

        mvc.perform(json(
                        "/api/members/email-verifications/confirmation",
                        "{\"email\": \"hyeja@example.com\", \"code\": \"000000\"}"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFY_001"))
                .andExpect(jsonPath("$.result.remainingAttempts").value(3));
    }

    @Test
    void returnsTooManyRequestsWhenLocked() throws Exception {
        doThrow(new GeneralException(ErrorStatus.VERIFY_TOO_MANY_FAILURES))
                .when(service).confirm("hyeja@example.com", "000000");

        mvc.perform(json(
                        "/api/members/email-verifications/confirmation",
                        "{\"email\": \"hyeja@example.com\", \"code\": \"000000\"}"
                ))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFY_005"));
    }

    @Test
    void rejectsCodeThatIsNotSixDigits() throws Exception {
        mvc.perform(json(
                        "/api/members/email-verifications/confirmation",
                        "{\"email\": \"hyeja@example.com\", \"code\": \"12ab\"}"
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.result.code").exists());
        verifyNoInteractions(service);
    }

    private RequestBuilder json(String url, String body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
