package com.hyeja.recommendationservice.policy.service;

import com.hyeja.recommendationservice.policy.dto.EligibilityPolicyDTO;
import com.hyeja.recommendationservice.policy.enums.EligibilityStatus;
import com.hyeja.recommendationservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.recommendationservice.profile.dto.EligibilityProfileDTO;
import com.hyeja.recommendationservice.profile.enums.IncomeRange;
import org.springframework.stereotype.Component;

@Component
public class PolicyIncomeEligibilityEvaluator {

    public EligibilityStatus evaluate(EligibilityPolicyDTO policy, EligibilityProfileDTO profile) {
        if (policy == null || policy.getIncomeConditionCode() == null) {
            return EligibilityStatus.UNKNOWN;
        }

        PolicyIncomeCondition condition = policy.getIncomeConditionCode();
        if (condition == PolicyIncomeCondition.NO_RESTRICTION) {
            return EligibilityStatus.ABLE;
        }
        if (condition != PolicyIncomeCondition.COMPARABLE
                || profile == null || profile.getIncomeRangeCode() == null) {
            return EligibilityStatus.UNKNOWN;
        }

        IncomeInterval memberIncome = intervalOf(profile.getIncomeRangeCode());
        Integer policyMin = policy.getIncomeMin();
        Integer policyMax = policy.getIncomeMax();
        if (policyMin == null && policyMax == null) {
            return EligibilityStatus.UNKNOWN;
        }

        if (isDisjoint(memberIncome, policyMin, policyMax)) {
            return EligibilityStatus.DISABLE;
        }
        if (isFullyIncluded(memberIncome, policyMin, policyMax)) {
            return EligibilityStatus.ABLE;
        }
        return EligibilityStatus.UNKNOWN;
    }

    private boolean isDisjoint(IncomeInterval member, Integer policyMin, Integer policyMax) {
        if (policyMax != null && member.minInclusive() > policyMax) {
            return true;
        }
        return policyMin != null
                && member.maxExclusive() != null
                && member.maxExclusive() <= policyMin;
    }

    private boolean isFullyIncluded(IncomeInterval member, Integer policyMin, Integer policyMax) {
        boolean satisfiesMinimum = policyMin == null || member.minInclusive() >= policyMin;
        boolean satisfiesMaximum = policyMax == null
                || member.maxExclusive() != null && member.maxExclusive() <= policyMax;
        return satisfiesMinimum && satisfiesMaximum;
    }

    private IncomeInterval intervalOf(IncomeRange range) {
        return switch (range) {
            case UNDER_2000 -> new IncomeInterval(0, 20_000_000);
            case R2000_3000 -> new IncomeInterval(20_000_000, 30_000_000);
            case R3000_4000 -> new IncomeInterval(30_000_000, 40_000_000);
            case R4000_5000 -> new IncomeInterval(40_000_000, 50_000_000);
            case OVER_5000 -> new IncomeInterval(50_000_000, null);
        };
    }

    private record IncomeInterval(int minInclusive, Integer maxExclusive) {
    }
}
