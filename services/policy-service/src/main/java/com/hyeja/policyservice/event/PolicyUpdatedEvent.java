package com.hyeja.policyservice.event;

import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

// policy-updated 이벤트 본문입니다. 필드는 docs/api/README.md 3장 계약과 같고, engagement가 policy_snapshot을 갱신할 때 씁니다.
// 공통 필드(eventId~aggregateId) 뒤에 정책 값을 평평하게 둡니다. enum은 이름 그대로(MONTHLY_RENT, SPECIFIC_PERIOD) 나갑니다.
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

    public static final String TYPE = "policy-updated";
    public static final int VERSION = 1;

    public static PolicyUpdatedEvent of(String eventId, OffsetDateTime occurredAt, Policy policy) {
        return new PolicyUpdatedEvent(eventId, TYPE, VERSION, occurredAt, policy.getPolicyId(),
                policy.getPolicyId(), policy.getPolicyName(), policy.getCategories(), policy.getSupportContent(),
                policy.getApplyEndDate(), policy.getApplyPeriodCode(), policy.getApplyUrl(), policy.getActiveYn(),
                policy.isDeleted());
    }
}
