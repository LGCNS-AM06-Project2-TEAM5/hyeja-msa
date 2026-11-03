package com.hyeja.engagementservice.member.service;

import com.hyeja.engagementservice.favorite.repository.FavoriteRepository;
import com.hyeja.engagementservice.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberEngagementCleanupService {

    private final NotificationRepository notificationRepository;
    private final FavoriteRepository favoriteRepository;

    /** 회원 탈퇴에 따른 Engagement 소유 데이터를 한 트랜잭션에서 hard delete합니다. */
    @Transactional
    public CleanupResult cleanup(Long memberId) {
        int deletedNotifications = notificationRepository.deleteAllByMemberId(memberId);
        int deletedFavorites = favoriteRepository.deleteAllByMemberId(memberId);
        return new CleanupResult(deletedFavorites, deletedNotifications);
    }

    public record CleanupResult(int deletedFavorites, int deletedNotifications) {
    }
}
