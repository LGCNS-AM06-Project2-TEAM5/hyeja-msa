package com.hyeja.recommendationservice.global.apiPayload.status;

import com.hyeja.recommendationservice.global.apiPayload.code.BaseCode;
import com.hyeja.recommendationservice.global.apiPayload.dto.ReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements BaseCode {
    OK(HttpStatus.OK, "SUCCESS_001", "성공입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ReasonDTO getReasonDTO() {
        return ReasonDTO.builder()
                .httpStatus(httpStatus).isSuccess(true)
                .code(code).message(message).build();
    }
}
