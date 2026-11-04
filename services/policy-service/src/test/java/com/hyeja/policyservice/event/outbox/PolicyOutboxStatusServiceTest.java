package com.hyeja.policyservice.event.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, PolicyOutboxStatusService.class})
class PolicyOutboxStatusServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 3, 21, 0);

    @Autowired private PolicyOutboxRepository repository;
    @Autowired private PolicyOutboxStatusService service;

    @Test
    void 대기_발행_건수와_마지막_발행_시각_최근_이벤트를_돌려준다() {
        save("e-1", "p-1", BASE, BASE.plusMinutes(1));
        save("e-2", "p-2", BASE.plusMinutes(2), BASE.plusMinutes(5));
        save("e-3", "p-3", BASE.plusMinutes(3), null);

        PolicyOutboxStatusDTO status = service.getStatus();

        assertThat(status.pendingCount()).isEqualTo(1);
        assertThat(status.publishedCount()).isEqualTo(2);
        assertThat(status.lastPublishedAt()).isEqualTo(BASE.plusMinutes(5));
        assertThat(status.recentEvents()).extracting(PolicyOutboxStatusDTO.RecentEvent::eventId)
                .containsExactly("e-3", "e-2", "e-1");
        assertThat(status.recentEvents().get(0).policyId()).isEqualTo("p-3");
        assertThat(status.recentEvents().get(0).publishedAt()).isNull();
    }

    @Test
    void 최근_이벤트는_10건까지만_보여_준다() {
        for (int i = 1; i <= 12; i++) {
            save("e-" + i, "p-" + i, BASE.plusMinutes(i), BASE.plusMinutes(i));
        }

        PolicyOutboxStatusDTO status = service.getStatus();

        assertThat(status.publishedCount()).isEqualTo(12);
        assertThat(status.recentEvents()).hasSize(10);
        assertThat(status.recentEvents().get(0).eventId()).isEqualTo("e-12");
    }

    @Test
    void 이벤트가_없으면_0건과_빈_목록을_돌려준다() {
        PolicyOutboxStatusDTO status = service.getStatus();

        assertThat(status.pendingCount()).isZero();
        assertThat(status.publishedCount()).isZero();
        assertThat(status.lastPublishedAt()).isNull();
        assertThat(status.recentEvents()).isEmpty();
    }

    private void save(String eventId, String policyId, LocalDateTime createdAt, LocalDateTime publishedAt) {
        PolicyOutbox row = new PolicyOutbox(eventId, policyId, "policy-updated", "{}", createdAt);
        if (publishedAt != null) {
            row.markPublished(publishedAt);
        }
        repository.save(row);
    }
}
