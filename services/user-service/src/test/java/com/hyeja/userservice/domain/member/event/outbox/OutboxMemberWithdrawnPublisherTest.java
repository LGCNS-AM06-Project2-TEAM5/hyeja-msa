package com.hyeja.userservice.domain.member.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hyeja.userservice.domain.member.event.MemberWithdrawnPublisher;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 탈퇴 트랜잭션과 Outbox가 함께 커밋·롤백되는지 검증합니다.
@SpringBootTest
@ActiveProfiles("test")
class OutboxMemberWithdrawnPublisherTest {

    @Autowired private MemberWithdrawnPublisher publisher;
    @Autowired private MemberOutboxRepository outbox;
    @Autowired private TransactionTemplate tx;
    @Autowired private JsonMapper jsonMapper;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
    }

    @Test
    void 탈퇴_이벤트를_계약_필드로_적어_둔다() {
        tx.executeWithoutResult(status -> publisher.publish(1L));

        List<MemberOutbox> rows = outbox.findAll();
        assertThat(rows).hasSize(1);
        MemberOutbox row = rows.get(0);
        assertThat(row.getAggregateId()).isEqualTo("1");
        assertThat(row.getEventType()).isEqualTo("member-withdrawn");
        assertThat(row.getPublishedAt()).isNull();

        JsonNode event = jsonMapper.readTree(row.getPayload());
        assertThat(event.get("eventId").asString()).isEqualTo(row.getEventId());
        assertThat(event.get("eventType").asString()).isEqualTo("member-withdrawn");
        assertThat(event.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(event.get("occurredAt").asString()).endsWith("+09:00");
        assertThat(event.get("aggregateId").asString()).isEqualTo("1");
        assertThat(event.get("memberId").asLong()).isEqualTo(1L);
    }

    @Test
    void 탈퇴_트랜잭션이_롤백되면_이벤트도_남지_않는다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            publisher.publish(1L);
            throw new IllegalStateException("탈퇴 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outbox.count()).isZero();
    }
}
