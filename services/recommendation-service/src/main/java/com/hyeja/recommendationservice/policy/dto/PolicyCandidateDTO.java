package com.hyeja.recommendationservice.policy.dto;

import com.hyeja.recommendationservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import com.hyeja.recommendationservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.recommendationservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.recommendationservice.policy.enums.PolicyIncomeCondition;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

// F2 응답의 정책 후보 한 건입니다. policy-service의 평면 필드를 null·Enum 값 그대로 받고, PolicyClientAdapter가 SearchPolicyDTO로 바꿉니다.
// regions는 정책의 전체 대상 지역이며 빈 목록은 전국 정책입니다. applyStartDate는 계약에 있지만 검색 코어에서 쓰지 않습니다.
public record PolicyCandidateDTO(
        String policyId,
        String policyName,
        List<PolicyCategory> categories,
        PolicyApplyPeriod applyPeriodCode,
        LocalDate applyStartDate,
        LocalDate applyEndDate,
        Boolean ageLimitYn,
        Integer minAge,
        Integer maxAge,
        PolicyIncomeCondition incomeConditionCode,
        Integer incomeMin,
        Integer incomeMax,
        String incomeEtc,
        Set<PolicyEmploymentCondition> employmentCodes,
        PolicyHouselessRequirement houselessRequirement,
        String extraQualification,
        List<Region> regions) {

    public record Region(String regionCode, String sigunguName) {
    }
}
