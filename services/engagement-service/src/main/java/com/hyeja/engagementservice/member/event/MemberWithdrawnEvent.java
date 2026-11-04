package com.hyeja.engagementservice.member.event;

import java.time.OffsetDateTime;

/** user-service가 member-withdrawn 토픽으로 발행하는 회원 탈퇴 이벤트 계약입니다. */
public record MemberWithdrawnEvent(
        String eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        String aggregateId,
        Long memberId) {

    public static final String TYPE = "member-withdrawn";
    public static final int VERSION = 1;

    public void validate() {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId는 필수입니다.");
        }
        if (!TYPE.equals(eventType)) {
            throw new IllegalArgumentException("지원하지 않는 eventType입니다: " + eventType);
        }
        if (eventVersion != VERSION) {
            throw new IllegalArgumentException("지원하지 않는 eventVersion입니다: " + eventVersion);
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt은 필수입니다.");
        }
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId는 양수여야 합니다.");
        }
        if (!memberId.toString().equals(aggregateId)) {
            throw new IllegalArgumentException("aggregateId와 memberId가 일치하지 않습니다.");
        }
    }
}
