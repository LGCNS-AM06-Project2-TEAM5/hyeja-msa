package com.hyeja.userservice.domain.member.event;

import java.time.OffsetDateTime;

// 회원 탈퇴 이벤트 계약입니다. engagement-service가 회원의 관심 정책과 알림을 정리할 때 사용합니다.
public record MemberWithdrawnEvent(
        String eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        String aggregateId,
        Long memberId) {

    public static final String TYPE = "member-withdrawn";
    public static final int VERSION = 1;

    public static MemberWithdrawnEvent of(String eventId, OffsetDateTime occurredAt, Long memberId) {
        String aggregateId = String.valueOf(memberId);
        return new MemberWithdrawnEvent(
                eventId,
                TYPE,
                VERSION,
                occurredAt,
                aggregateId,
                memberId
        );
    }
}
