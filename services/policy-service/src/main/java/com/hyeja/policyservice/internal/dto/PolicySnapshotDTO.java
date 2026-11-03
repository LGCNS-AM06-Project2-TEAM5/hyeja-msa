package com.hyeja.policyservice.internal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import java.time.LocalDate;
import java.util.Set;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PolicySnapshotDTO(
        String policyId,
        String policyName,
        Set<PolicyCategory> categories,
        String supportContent,
        LocalDate applyEndDate,
        PolicyApplyPeriod applyPeriodCode,
        String applyUrl,
        Boolean activeYn,
        boolean deleted) {

    public static PolicySnapshotDTO from(Policy policy) {
        return new PolicySnapshotDTO(
                policy.getPolicyId(), policy.getPolicyName(), policy.getCategories(),
                policy.getSupportContent(), policy.getApplyEndDate(), policy.getApplyPeriodCode(),
                policy.getApplyUrl(), policy.getActiveYn(), policy.isDeleted());
    }
}
