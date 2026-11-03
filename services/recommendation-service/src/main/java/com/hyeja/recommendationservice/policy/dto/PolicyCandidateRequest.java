package com.hyeja.recommendationservice.policy.dto;

import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import java.time.LocalDate;
import java.util.List;

// F2 요청(POST /internal/policies/candidates). 빈 categories는 전체 카테고리이며, today는 한국 시간 기준 날짜입니다.
// 정렬과 최대 20건은 policy-service가 고정하므로 limit 필드는 없습니다.
public record PolicyCandidateRequest(
        List<PolicyCategory> categories,
        String regionCode,
        LocalDate today) {
}
