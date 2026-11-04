package com.hyeja.recommendationservice.global.exception;

import com.hyeja.recommendationservice.global.apiPayload.code.BaseErrorCode;
import com.hyeja.recommendationservice.global.apiPayload.dto.ReasonDTO;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {
    private final BaseErrorCode code;
    // 에러 응답의 result에 함께 내려줄 값입니다. 없으면 null.
    private final Object result;

    public GeneralException(BaseErrorCode code) {
        this(code, null);
    }

    public GeneralException(BaseErrorCode code, Object result) {
        super(code.getMessage());
        this.code = code;
        this.result = result;
    }

    public ReasonDTO getErrorReason() {
        return code.getErrorReasonDTO();
    }
}
