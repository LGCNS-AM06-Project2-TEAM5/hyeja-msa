package com.hyeja.recommendationservice.policy.dto;

import com.hyeja.recommendationservice.policy.enums.EligibilityConditionType;
import com.hyeja.recommendationservice.policy.enums.EligibilityStatus;

// 1차 PolicyDetailResponseDTO.ConditionResultDTO와 같은 필드입니다.
public record ConditionResultDTO(
        EligibilityConditionType type,
        EligibilityStatus status,
        String policyCondition,
        String memberValue) {
}
