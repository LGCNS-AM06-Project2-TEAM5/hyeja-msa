package com.hyeja.policyservice.policy.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PolicyApplyPeriod {
    SPECIFIC_PERIOD("특정기간"),
    ALWAYS("상시"),
    CLOSED("마감");

    private final String label;
}
