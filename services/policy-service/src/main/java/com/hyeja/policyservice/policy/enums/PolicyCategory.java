package com.hyeja.policyservice.policy.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor

public enum PolicyCategory {
    MONTHLY_RENT("월세"),
    JEONSE("전세"),
    PURCHASE("청약·구입"),
    PUBLIC_RENT("공공임대"),
    OTHER("기타 주거");

    private final String label;
}
