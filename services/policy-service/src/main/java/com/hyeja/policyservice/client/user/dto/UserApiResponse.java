package com.hyeja.policyservice.client.user.dto;

// user-service가 감싸 보내는 ApiResponse 모양입니다. policy의 ApiResponse는 응답 전용이라 받을 때는 이 record를 씁니다.
public record UserApiResponse<T>(Boolean isSuccess, String code, String message, T result) {
}
