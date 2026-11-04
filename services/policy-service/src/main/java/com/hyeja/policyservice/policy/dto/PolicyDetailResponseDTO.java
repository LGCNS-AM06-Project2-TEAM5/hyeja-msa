package com.hyeja.policyservice.policy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hyeja.policyservice.policy.enums.EligibilityConditionType;
import com.hyeja.policyservice.policy.enums.EligibilityStatus;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record PolicyDetailResponseDTO(
        String policyId,
        String policyName,
        Set<PolicyCategory> categories,
        List<String> categoryLabels,
        String apiSubCategory,
        String keywords,
        String description,
        String supportContent,
        String extraQualification,
        List<TermSummaryDTO> terms,
        PolicyApplyPeriod applyPeriod,
        String applyPeriodLabel,
        LocalDate applyStartDate,
        LocalDate applyEndDate,
        String applyMethod,
        String applyUrl,
        String refUrl,
        Boolean activeYn,
        @JsonProperty("isFavorite") boolean isFavorite,
        EligibilityStatus overallStatus,
        List<ConditionResultDTO> conditions) {

    public record TermSummaryDTO(Long termId, String termName) {
    }

    public record ConditionResultDTO(
            EligibilityConditionType type,
            EligibilityStatus status,
            String policyCondition,
            String memberValue) {
    }
}
