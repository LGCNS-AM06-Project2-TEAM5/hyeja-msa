package com.hyeja.engagementservice.policy.event;

import com.hyeja.engagementservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.engagementservice.policy.enums.PolicyCategory;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

/** policy-service가 보내는 policy-updated 본문(docs/api/README.md 3장). 공통 필드와 정책 값이 한 객체에 있습니다. */
public record PolicyUpdatedEvent(
        String eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        String aggregateId,
        String policyId,
        String policyName,
        Set<PolicyCategory> categories,
        String supportContent,
        LocalDate applyEndDate,
        PolicyApplyPeriod applyPeriodCode,
        String applyUrl,
        Boolean activeYn,
        boolean deleted) {
}
