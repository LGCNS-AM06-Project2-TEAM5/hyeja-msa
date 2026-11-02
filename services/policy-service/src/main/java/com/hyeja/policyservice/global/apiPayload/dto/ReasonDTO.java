package com.hyeja.policyservice.global.apiPayload.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@Builder
public class ReasonDTO {
    private final HttpStatus httpStatus;
    private final Boolean isSuccess;
    private final String code;
    private final String message;
}
