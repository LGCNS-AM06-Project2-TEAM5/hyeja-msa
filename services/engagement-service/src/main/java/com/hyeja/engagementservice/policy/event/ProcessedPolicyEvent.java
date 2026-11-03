package com.hyeja.engagementservice.policy.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이미 반영한 policy-updated 이벤트. 같은 eventId가 다시 와도 스냅샷을 두 번 고치지 않도록 남겨 둡니다. */
@Getter
@Entity
@Table(name = "processed_policy_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedPolicyEvent {
    @Id @Column(name = "event_id", length = 36) private String eventId;
    @Column(name = "policy_id", nullable = false, length = 100) private String policyId;
    @Column(name = "processed_at", nullable = false) private LocalDateTime processedAt;

    public ProcessedPolicyEvent(String eventId, String policyId, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.policyId = policyId;
        this.processedAt = processedAt;
    }
}
