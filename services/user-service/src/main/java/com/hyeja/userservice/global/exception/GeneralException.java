package com.hyeja.userservice.global.exception;

import com.hyeja.userservice.global.apiPayload.code.BaseErrorCode;
import com.hyeja.userservice.global.apiPayload.dto.ReasonDTO;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode code;
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
