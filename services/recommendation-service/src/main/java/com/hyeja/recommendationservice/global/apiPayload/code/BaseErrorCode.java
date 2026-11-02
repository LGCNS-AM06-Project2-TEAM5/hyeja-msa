package com.hyeja.recommendationservice.global.apiPayload.code;

import com.hyeja.recommendationservice.global.apiPayload.dto.ReasonDTO;
import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
    ReasonDTO getErrorReasonDTO();
}
