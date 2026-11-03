package com.hyeja.engagementservice.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hyeja.engagementservice.favorite.repository.FavoriteRepository;
import com.hyeja.engagementservice.member.event.MemberWithdrawnEvent;
import com.hyeja.engagementservice.member.event.ProcessedMemberEventRepository;
import com.hyeja.engagementservice.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MemberEngagementCleanupServiceTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final FavoriteRepository favorites = mock(FavoriteRepository.class);
    private final ProcessedMemberEventRepository processedEvents = mock(ProcessedMemberEventRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC);
    private final MemberEngagementCleanupService service =
            new MemberEngagementCleanupService(notifications, favorites, processedEvents, clock);

    @Test
    void 이미_처리된_eventId면_삭제를_수행하지_않는다() {
        MemberWithdrawnEvent event = event("withdraw-1", 1L);
        when(processedEvents.existsByEventId(event.eventId())).thenReturn(true);

        var result = service.process(event);

        assertThat(result.processed()).isFalse();
        verify(processedEvents, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(notifications, never()).deleteAllByMemberId(event.memberId());
        verify(favorites, never()).deleteAllByMemberId(event.memberId());
    }

    private MemberWithdrawnEvent event(String eventId, long memberId) {
        return new MemberWithdrawnEvent(
                eventId,
                MemberWithdrawnEvent.TYPE,
                MemberWithdrawnEvent.VERSION,
                OffsetDateTime.parse("2026-11-04T09:00:00+09:00"),
                Long.toString(memberId),
                memberId);
    }
}
