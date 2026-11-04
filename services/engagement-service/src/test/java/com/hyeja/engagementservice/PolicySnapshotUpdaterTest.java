package com.hyeja.engagementservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.engagementservice.policy.enums.PolicyCategory;
import com.hyeja.engagementservice.policy.event.PolicyUpdatedEvent;
import com.hyeja.engagementservice.policy.event.ProcessedPolicyEventRepository;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import com.hyeja.engagementservice.policy.service.PolicySnapshotUpdater;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("test") @Transactional
class PolicySnapshotUpdaterTest {
    @Autowired PolicySnapshotUpdater updater;
    @Autowired PolicyRepository policies;
    @Autowired ProcessedPolicyEventRepository processed;

    @Test void 처음_보는_정책이면_스냅샷을_만든다() {
        assertThat(updater.apply(event("e-1", "P1", "청년 월세", true, false))).isTrue();

        Policy policy = policies.findById("P1").orElseThrow();
        assertThat(policy.getPolicyName()).isEqualTo("청년 월세");
        assertThat(policy.getCategories()).containsExactly(PolicyCategory.MONTHLY_RENT);
        assertThat(policy.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(policy.getApplyPeriodCode()).isEqualTo(PolicyApplyPeriod.SPECIFIC_PERIOD);
        assertThat(policy.getActiveYn()).isTrue();
        assertThat(processed.existsById("e-1")).isTrue();
    }

    @Test void 같은_eventId가_다시_오면_한_번만_반영한다() {
        updater.apply(event("e-1", "P1", "청년 월세", true, false));

        assertThat(updater.apply(event("e-1", "P1", "다른 이름", false, false))).isFalse();

        assertThat(policies.findById("P1").orElseThrow().getPolicyName()).isEqualTo("청년 월세");
        assertThat(processed.count()).isEqualTo(1);
    }

    @Test void 새_이벤트면_정책명과_비활성_여부를_고친다() {
        updater.apply(event("e-1", "P1", "청년 월세", true, false));

        updater.apply(event("e-2", "P1", "청년 월세 2차", false, false));

        Policy policy = policies.findById("P1").orElseThrow();
        assertThat(policy.getPolicyName()).isEqualTo("청년 월세 2차");
        assertThat(policy.getActiveYn()).isFalse();
    }

    @Test void 삭제_이벤트면_삭제_표시하고_없는_정책은_새로_만들지_않는다() {
        updater.apply(event("e-1", "P1", "청년 월세", true, false));

        updater.apply(event("e-2", "P1", "청년 월세", true, true));
        updater.apply(event("e-3", "P2", "처음 보는 정책", true, true));

        assertThat(policies.findById("P1").orElseThrow().isDeleted()).isTrue();
        assertThat(policies.findById("P2")).isEmpty();
        assertThat(processed.existsById("e-3")).isTrue();
    }

    private static PolicyUpdatedEvent event(String eventId, String policyId, String name, boolean active, boolean deleted) {
        return new PolicyUpdatedEvent(eventId, "policy-updated", 1, OffsetDateTime.parse("2026-11-03T17:00:00+09:00"),
                policyId, policyId, name, Set.of(PolicyCategory.MONTHLY_RENT), "월 20만 원",
                LocalDate.of(2026, 12, 31), PolicyApplyPeriod.SPECIFIC_PERIOD, "https://example.com/apply", active, deleted);
    }
}
