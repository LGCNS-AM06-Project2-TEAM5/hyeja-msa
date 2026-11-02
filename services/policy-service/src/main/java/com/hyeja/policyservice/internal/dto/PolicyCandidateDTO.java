package com.hyeja.policyservice.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PolicyCandidateDTO(
        String policyId,
        String policyName,
        Set<PolicyCategory> categories,
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
        List<PolicyCandidateRegionDTO> regions) {

    public static PolicyCandidateDTO from(Policy policy, List<PolicyRegion> regions) {
        return new PolicyCandidateDTO(
                policy.getPolicyId(), policy.getPolicyName(), policy.getCategories(),
                policy.getApplyPeriodCode(), policy.getApplyStartDate(), policy.getApplyEndDate(),
                policy.getAgeLimitYn(), policy.getMinAge(), policy.getMaxAge(),
                policy.getIncomeConditionCode(), policy.getIncomeMin(), policy.getIncomeMax(),
                policy.getIncomeEtc(), policy.getEmploymentCodes(), policy.getHouselessRequirement(),
                policy.getExtraQualification(), regions.stream()
                        .map(link -> new PolicyCandidateRegionDTO(
                                link.getRegion().getRegionCode(), link.getRegion().getSigunguName()))
                        .toList());
    }

    public record PolicyCandidateRegionDTO(String regionCode, String sigunguName) {
    }
}
