package com.hyeja.engagementservice.client.policy.dto;

import com.hyeja.engagementservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.engagementservice.policy.enums.PolicyCategory;
import java.time.LocalDate;
import java.util.Set;

/** F5 응답. Policy Service의 엔티티를 공유하지 않고 계약의 값만 받습니다. */
public record PolicySnapshotDTO(
        String policyId,
        String policyName,
        Set<PolicyCategory> categories,
        String supportContent,
        LocalDate applyEndDate,
        PolicyApplyPeriod applyPeriodCode,
        String applyUrl,
        Boolean activeYn,
        Boolean deleted) {
}
