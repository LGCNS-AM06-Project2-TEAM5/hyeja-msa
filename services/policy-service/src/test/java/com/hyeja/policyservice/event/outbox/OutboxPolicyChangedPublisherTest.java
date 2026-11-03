package com.hyeja.policyservice.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyeja.policyservice.event.PolicyChangedPublisher;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 정책 저장과 같은 트랜잭션에서 policy_outbox에 이벤트가 함께 커밋·롤백되는지 봅니다.
// 커밋 여부가 핵심이라 테스트 트랜잭션으로 감싸지 않고, 끝나면 직접 지웁니다.
@SpringBootTest
@ActiveProfiles("test")
class OutboxPolicyChangedPublisherTest {

    @Autowired private PolicyChangedPublisher publisher;
    @Autowired private PolicyRepository policies;
    @Autowired private PolicyOutboxRepository outbox;
    @Autowired private TransactionTemplate tx;
    @Autowired private JsonMapper jsonMapper;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        policies.deleteAllById(List.of("outbox-1", "outbox-2"));
    }

    @Test
    void 저장한_정책을_계약_필드로_적어_둔다() {
        tx.executeWithoutResult(status -> {
            policies.save(policy("outbox-1"));
            publisher.publish(List.of("outbox-1"));
        });

        List<PolicyOutbox> rows = outbox.findAll();
        assertThat(rows).hasSize(1);
        PolicyOutbox row = rows.get(0);
        assertThat(row.getAggregateId()).isEqualTo("outbox-1");
        assertThat(row.getEventType()).isEqualTo("policy-updated");
        assertThat(row.getPublishedAt()).isNull();

        JsonNode event = jsonMapper.readTree(row.getPayload());
        assertThat(event.get("eventId").asString()).isEqualTo(row.getEventId());
        assertThat(event.get("eventType").asString()).isEqualTo("policy-updated");
        assertThat(event.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.get("occurredAt").asString()).endsWith("+09:00");
        assertThat(event.get("aggregateId").asString()).isEqualTo("outbox-1");
        assertThat(event.get("policyId").asString()).isEqualTo("outbox-1");
        assertThat(event.get("policyName").asString()).isEqualTo("청년 월세 outbox-1");
        assertThat(event.get("categories").get(0).asString()).isEqualTo("MONTHLY_RENT");
        assertThat(event.get("supportContent").asString()).isEqualTo("월 20만 원");
        assertThat(event.get("applyEndDate").asString()).isEqualTo("2026-10-31");
        assertThat(event.get("applyPeriodCode").asString()).isEqualTo("SPECIFIC_PERIOD");
        assertThat(event.get("applyUrl").asString()).isEqualTo("https://example.com/apply");
        assertThat(event.get("activeYn").asBoolean()).isTrue();
        assertThat(event.get("deleted").asBoolean()).isFalse();
    }

    @Test
    void 정책_저장이_롤백되면_이벤트도_남지_않는다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            policies.save(policy("outbox-1"));
            publisher.publish(List.of("outbox-1"));
            throw new IllegalStateException("저장 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(policies.findById("outbox-1")).isEmpty();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void 같은_ID가_겹치면_한_번만_적고_없는_정책은_건너뛴다() {
        tx.executeWithoutResult(status -> {
            policies.save(policy("outbox-1"));
            policies.save(policy("outbox-2"));
            publisher.publish(List.of("outbox-1", "outbox-1", "missing", "outbox-2"));
        });

        assertThat(outbox.findAll()).extracting(PolicyOutbox::getAggregateId)
                .containsExactly("outbox-1", "outbox-2");
        assertThat(outbox.findAll()).extracting(PolicyOutbox::getEventId).doesNotHaveDuplicates();
    }

    private static Policy policy(String id) {
        return Policy.builder().policyId(id).policyName("청년 월세 " + id)
                .categories(Set.of(PolicyCategory.MONTHLY_RENT))
                .supportContent("월 20만 원")
                .ageLimitYn(false)
                .houselessRequirement(PolicyHouselessRequirement.REQUIRED)
                .applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD)
                .applyStartDate(LocalDate.of(2026, 9, 1)).applyEndDate(LocalDate.of(2026, 10, 31))
                .applyUrl("https://example.com/apply")
                .viewCount(0).activeYn(true).build();
    }
}
