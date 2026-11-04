package com.hyeja.engagementservice.member.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "dlt_alert_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DltAlertState {
    @Id
    @Column(name = "dlt_topic", length = 200)
    private String dltTopic;
    @Column(name = "failure_count", nullable = false)
    private int failureCount;
    @Column(name = "notification_pending", nullable = false)
    private boolean notificationPending;
    @Column(name = "latest_event_key", length = 200)
    private String latestEventKey;
    @Lob
    @Column(name = "latest_payload")
    private String latestPayload;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public DltAlertState(String dltTopic) {
        this.dltTopic = dltTopic;
        this.updatedAt = LocalDateTime.now();
    }

    public int recordFailure(String eventKey, String payload, int threshold) {
        failureCount++;
        latestEventKey = eventKey;
        latestPayload = payload;
        updatedAt = LocalDateTime.now();
        if (failureCount >= threshold) notificationPending = true;
        return failureCount;
    }

    public void markNotified() {
        failureCount = 0;
        notificationPending = false;
        updatedAt = LocalDateTime.now();
    }
}
