package com.hyeja.policyservice.policy.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PolicyMarriageCondition {
    MARRIED("기혼"),
    SINGLE("미혼"),
    NO_RESTRICTION("제한없음");

    private final String label;
}
