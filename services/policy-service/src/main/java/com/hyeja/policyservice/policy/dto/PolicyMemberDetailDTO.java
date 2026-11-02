package com.hyeja.policyservice.policy.dto;

import com.hyeja.policyservice.policy.enums.EligibilityConditionType;
import com.hyeja.policyservice.policy.enums.EligibilityStatus;
import java.util.List;

// 정책 상세 응답 중 로그인 회원에게만 채우는 부분(하트·판정)입니다.
// 상세 응답(PolicyDetailResponseDTO)은 이 값을 그대로 옮겨 담습니다.
public record PolicyMemberDetailDTO(
        boolean isFavorite,
        EligibilityStatus overallStatus,
        List<ConditionResultDTO> conditions) {

    // 비로그인 상세: 하트 false, 판정 없음(1차와 같음).
    public static PolicyMemberDetailDTO guest() {
        return new PolicyMemberDetailDTO(false, null, List.of());
    }

    public record ConditionResultDTO(
            EligibilityConditionType type,
            EligibilityStatus status,
            String policyCondition,
            String memberValue) {
    }
}
