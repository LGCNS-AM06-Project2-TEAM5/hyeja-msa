package com.hyeja.engagementservice.global.apiPayload.code;

import com.hyeja.engagementservice.global.apiPayload.dto.ReasonDTO;
import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
    HttpStatus getHttpStatus();
    String getCode();
    String getMessage();
    ReasonDTO getErrorReasonDTO();
}
