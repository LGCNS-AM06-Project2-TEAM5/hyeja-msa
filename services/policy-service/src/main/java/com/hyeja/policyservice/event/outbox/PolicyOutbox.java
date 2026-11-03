package com.hyeja.policyservice.event.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 아직 Kafka로 보내지 않은 이벤트를 정책 저장과 같은 트랜잭션에 적어 두는 표입니다(Outbox).
// published_at이 비어 있는 행이 보낼 대상이고, 보낸 뒤에 시각을 채웁니다.
@Getter
@Entity
@Table(name = "policy_outbox",
        indexes = @Index(name = "idx_policy_outbox_unpublished", columnList = "published_at, outbox_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36, updatable = false)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false, length = 30, updatable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public PolicyOutbox(String eventId, String aggregateId, String eventType, String payload,
            LocalDateTime createdAt) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }
}
