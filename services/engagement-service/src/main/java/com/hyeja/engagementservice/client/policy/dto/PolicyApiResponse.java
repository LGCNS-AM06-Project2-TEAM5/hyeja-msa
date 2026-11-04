package com.hyeja.engagementservice.client.policy.dto;

public record PolicyApiResponse<T>(Boolean isSuccess, String code, String message, T result) {
}
