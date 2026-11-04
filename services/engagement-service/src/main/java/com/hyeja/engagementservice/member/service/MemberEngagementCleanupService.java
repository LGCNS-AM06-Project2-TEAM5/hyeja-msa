package com.hyeja.engagementservice.member.service;

import com.hyeja.engagementservice.favorite.repository.FavoriteRepository;
import com.hyeja.engagementservice.member.event.MemberWithdrawnEvent;
import com.hyeja.engagementservice.member.event.ProcessedMemberEvent;
import com.hyeja.engagementservice.member.event.ProcessedMemberEventRepository;
import com.hyeja.engagementservice.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberEngagementCleanupService {

    private final NotificationRepository notificationRepository;
    private final FavoriteRepository favoriteRepository;
    private final ProcessedMemberEventRepository processedEventRepository;
    private final Clock clock;

    @Transactional
    public CleanupResult process(MemberWithdrawnEvent event) {
        if (processedEventRepository.existsByEventId(event.eventId())) {
            return CleanupResult.skipped();
        }

        processedEventRepository.saveAndFlush(new ProcessedMemberEvent(
                event.eventId(), event.eventType(), event.memberId(), LocalDateTime.now(clock)));

        int deletedNotifications = notificationRepository.deleteAllByMemberId(event.memberId());
        int deletedFavorites = favoriteRepository.deleteAllByMemberId(event.memberId());
        return new CleanupResult(true, deletedFavorites, deletedNotifications);
    }

    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return processedEventRepository.existsByEventId(eventId);
    }

    public record CleanupResult(boolean processed, int deletedFavorites, int deletedNotifications) {
        private static CleanupResult skipped() {
            return new CleanupResult(false, 0, 0);
        }
    }
}
