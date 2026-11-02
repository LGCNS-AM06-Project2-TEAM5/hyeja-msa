package com.hyeja.recommendationservice.policy.dto;

import com.hyeja.recommendationservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import com.hyeja.recommendationservice.region.dto.RegionDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 검색 후보 정책 한 건. 1차 Policy 엔티티 중 검색 응답·자격 판정·AI 검토에 쓰는 값만 담습니다.
 * categories는 1차 PolicyCategoryConverter처럼 이름 오름차순 순서를 유지해야 응답 JSON 배열 순서가 같습니다.
 * regions는 1차 PolicyRegionRepository.findAllActiveByPolicyIds처럼 삭제되지 않은 정책-지역·지역만, 지역 코드 오름차순입니다.
 */
public record SearchPolicyDTO(
        String policyId,
        String policyName,
        Set<PolicyCategory> categories,
        LocalDate applyEndDate,
        PolicyApplyPeriod applyPeriodCode,
        String extraQualification,
        EligibilityPolicyDTO eligibility,
        List<RegionDTO> regions) {
}
