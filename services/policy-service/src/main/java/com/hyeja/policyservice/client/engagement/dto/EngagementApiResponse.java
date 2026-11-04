package com.hyeja.policyservice.client.engagement.dto;

// engagement-service가 감싸 보내는 ApiResponse 모양입니다.
public record EngagementApiResponse<T>(Boolean isSuccess, String code, String message, T result) {
}
