package com.hyeja.policyservice.event.outbox;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

// 관리자 화면에 보여 줄 policy-updated 발행 상태입니다. payload는 크기가 커서 넣지 않습니다.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PolicyOutboxStatusDTO(
        long pendingCount,
        long publishedCount,
        LocalDateTime lastPublishedAt,
        List<RecentEvent> recentEvents) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RecentEvent(
            String eventId,
            String policyId,
            String eventType,
            LocalDateTime createdAt,
            LocalDateTime publishedAt) {

        static RecentEvent from(PolicyOutbox row) {
            return new RecentEvent(row.getEventId(), row.getAggregateId(), row.getEventType(),
                    row.getCreatedAt(), row.getPublishedAt());
        }
    }
}
