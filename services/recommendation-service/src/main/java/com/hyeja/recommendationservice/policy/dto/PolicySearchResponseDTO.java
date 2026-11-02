package com.hyeja.recommendationservice.policy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hyeja.recommendationservice.policy.enums.EligibilityStatus;
import com.hyeja.recommendationservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

// 1차 PolicySearchResponseDTO와 같은 JSON 계약입니다(필드명·순서·타입).
public record PolicySearchResponseDTO(
        int approvedCount,
        int underReviewCount,
        int declinedCount,
        List<PolicySearchItemDTO> approved,
        List<PolicySearchItemDTO> underReview,
        List<PolicySearchItemDTO> declined) {

    public PolicySearchResponseDTO(
            List<PolicySearchItemDTO> approved,
            List<PolicySearchItemDTO> underReview,
            List<PolicySearchItemDTO> declined) {
        this(approved.size(), underReview.size(), declined.size(), approved, underReview, declined);
    }

    public record PolicySearchItemDTO(
            String policyId,
            String policyName,
            Set<PolicyCategory> categories,
            LocalDate applyEndDate,
            PolicyApplyPeriod applyPeriod,
            @JsonProperty("isFavorite") boolean isFavorite,
            String aiReason,
            PolicyEligibilityStatusDTO status) {
    }

    public record PolicyEligibilityStatusDTO(
            EligibilityStatus age,
            EligibilityStatus region,
            EligibilityStatus income,
            EligibilityStatus employment,
            EligibilityStatus houseless) {
    }
}
