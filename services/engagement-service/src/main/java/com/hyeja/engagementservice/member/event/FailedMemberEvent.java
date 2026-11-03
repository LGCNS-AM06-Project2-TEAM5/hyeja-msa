package com.hyeja.engagementservice.member.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자 확인과 향후 누적 건수 알림에 사용할 member-withdrawn DLT 보관 레코드입니다. */
@Getter
@Entity
@Table(name = "failed_member_event", uniqueConstraints = @UniqueConstraint(
        name = "uk_failed_member_event_dlt_position",
        columnNames = {"dlt_topic", "dlt_partition", "dlt_offset"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailedMemberEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "failed_event_id")
    private Long failedEventId;

    @Column(name = "dlt_topic", nullable = false, length = 200)
    private String dltTopic;

    @Column(name = "dlt_partition", nullable = false)
    private int dltPartition;

    @Column(name = "dlt_offset", nullable = false)
    private long dltOffset;

    @Column(name = "event_key", length = 200)
    private String eventKey;

    @Lob
    @Column(name = "payload")
    private String payload;

    @Column(name = "original_topic", length = 200)
    private String originalTopic;

    @Column(name = "original_partition")
    private Integer originalPartition;

    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "exception_message", length = 1000)
    private String exceptionMessage;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public FailedMemberEvent(
            String dltTopic,
            int dltPartition,
            long dltOffset,
            String eventKey,
            String payload,
            String originalTopic,
            Integer originalPartition,
            Long originalOffset,
            String exceptionMessage,
            LocalDateTime receivedAt) {
        this.dltTopic = dltTopic;
        this.dltPartition = dltPartition;
        this.dltOffset = dltOffset;
        this.eventKey = eventKey;
        this.payload = payload;
        this.originalTopic = originalTopic;
        this.originalPartition = originalPartition;
        this.originalOffset = originalOffset;
        this.exceptionMessage = exceptionMessage;
        this.receivedAt = receivedAt;
    }

    public void markResolved(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
