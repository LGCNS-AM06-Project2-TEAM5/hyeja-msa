package com.hyeja.policyservice.policy.service;

import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.EligibilityStatus;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.enums.IncomeRange;
import org.springframework.stereotype.Component;

// 1차(hyeja-backend 5c7ff86) 판정 규칙 그대로입니다. recommendation에도 같은 규칙이 있고 기준은 recommendation이 가집니다(#1 F8).
// 규칙을 바꾸면 양쪽을 함께 바꿉니다.
@Component
public class PolicyIncomeEligibilityEvaluator {

    public EligibilityStatus evaluate(Policy policy, MemberProfileDTO profile) {
        if (policy == null || policy.getIncomeConditionCode() == null) {
            return EligibilityStatus.UNKNOWN;
        }

        PolicyIncomeCondition condition = policy.getIncomeConditionCode();
        if (condition == PolicyIncomeCondition.NO_RESTRICTION) {
            return EligibilityStatus.ABLE;
        }
        if (condition != PolicyIncomeCondition.COMPARABLE
                || profile == null || profile.incomeRangeCode() == null) {
            return EligibilityStatus.UNKNOWN;
        }

        IncomeInterval memberIncome = intervalOf(profile.incomeRangeCode());
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
