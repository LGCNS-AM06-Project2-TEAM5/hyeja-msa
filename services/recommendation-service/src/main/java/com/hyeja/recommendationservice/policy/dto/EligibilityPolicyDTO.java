package com.hyeja.recommendationservice.policy.dto;

import com.hyeja.recommendationservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.recommendationservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.recommendationservice.policy.enums.PolicyIncomeCondition;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

// 자격 판정에 쓰는 정책 조건만 담습니다. 1차 Policy 엔티티의 같은 이름 필드에 대응합니다.
@Getter
@Builder
public class EligibilityPolicyDTO {
    private final Integer minAge;
    private final Integer maxAge;
    private final Boolean ageLimitYn;
    private final PolicyIncomeCondition incomeConditionCode;
    private final Integer incomeMin;
    private final Integer incomeMax;
    private final String incomeEtc;
    private final Set<PolicyEmploymentCondition> employmentCodes;
    private final PolicyHouselessRequirement houselessRequirement;
}
