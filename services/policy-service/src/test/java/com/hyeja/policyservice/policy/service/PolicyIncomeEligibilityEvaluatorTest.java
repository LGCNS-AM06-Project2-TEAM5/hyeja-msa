package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.EligibilityStatus;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.enums.IncomeRange;
import org.junit.jupiter.api.Test;

class PolicyIncomeEligibilityEvaluatorTest {

    private final PolicyIncomeEligibilityEvaluator evaluator =
            new PolicyIncomeEligibilityEvaluator();

    @Test
    void returnsYWhenMemberRangeIsFullyWithinPolicyRange() {
        assertThat(evaluator.evaluate(
                policy(PolicyIncomeCondition.COMPARABLE, null, 50_000_000),
                profile(IncomeRange.R4000_5000)))
                .isEqualTo(EligibilityStatus.ABLE);
    }

    @Test
    void returnsNWhenMemberRangeDoesNotOverlapPolicyRange() {
        assertThat(evaluator.evaluate(
                policy(PolicyIncomeCondition.COMPARABLE, null, 30_000_000),
                profile(IncomeRange.R4000_5000)))
                .isEqualTo(EligibilityStatus.DISABLE);
    }

    @Test
    void returnsUWhenMemberRangeOnlyPartiallyOverlapsPolicyRange() {
        assertThat(evaluator.evaluate(
                policy(PolicyIncomeCondition.COMPARABLE, null, 45_000_000),
                profile(IncomeRange.R4000_5000)))
                .isEqualTo(EligibilityStatus.UNKNOWN);
    }

    @Test
    void handlesNoRestrictionConditionalAndMissingProfileIncome() {
        assertThat(evaluator.evaluate(
                policy(PolicyIncomeCondition.NO_RESTRICTION, null, null), null))
                .isEqualTo(EligibilityStatus.ABLE);
        assertThat(evaluator.evaluate(
                policy(PolicyIncomeCondition.CONDITIONAL, null, null),
                profile(IncomeRange.UNDER_2000)))
                .isEqualTo(EligibilityStatus.UNKNOWN);
        assertThat(evaluator.evaluate(
                policy(PolicyIncomeCondition.COMPARABLE, null, 50_000_000),
                profile(null)))
                .isEqualTo(EligibilityStatus.UNKNOWN);
    }

    private Policy policy(
            PolicyIncomeCondition condition, Integer minimum, Integer maximum) {
        Policy policy = mock(Policy.class);
        when(policy.getIncomeConditionCode()).thenReturn(condition);
        when(policy.getIncomeMin()).thenReturn(minimum);
        when(policy.getIncomeMax()).thenReturn(maximum);
        return policy;
    }

    private MemberProfileDTO profile(IncomeRange incomeRange) {
        MemberProfileDTO profile = mock(MemberProfileDTO.class);
        when(profile.incomeRangeCode()).thenReturn(incomeRange);
        return profile;
    }
}
