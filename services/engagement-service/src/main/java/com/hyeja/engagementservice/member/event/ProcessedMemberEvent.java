package com.hyeja.engagementservice.member.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processed_member_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMemberEvent {

    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public ProcessedMemberEvent(String eventId, String eventType, Long memberId, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.memberId = memberId;
        this.processedAt = processedAt;
    }
}
