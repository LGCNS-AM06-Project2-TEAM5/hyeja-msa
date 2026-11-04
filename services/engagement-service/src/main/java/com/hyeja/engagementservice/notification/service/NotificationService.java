package com.hyeja.engagementservice.notification.service;

import com.hyeja.engagementservice.notification.converter.NotificationConverter;
import com.hyeja.engagementservice.notification.dto.NotificationResponseDTO.NotificationItemDTO;
import com.hyeja.engagementservice.notification.dto.NotificationResponseDTO.NotificationListDTO;
import com.hyeja.engagementservice.notification.entity.Notification;
import com.hyeja.engagementservice.notification.repository.NotificationRepository;
import com.hyeja.engagementservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.engagementservice.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationListDTO getNotifications(Long memberId, int page, int size) {

        Page<Notification> notificationPage = notificationRepository.findAllByMemberId(
                memberId,
                PageRequest.of(page, size)
        );
        long unreadCount = notificationRepository
                .countByMemberIdAndReadYnFalseAndDeletedAtIsNull(memberId);
        return NotificationConverter.toNotificationListDTO(notificationPage, unreadCount);
    }

    @Transactional
    public NotificationItemDTO markAsRead(Long memberId, Long notificationId) {

        Notification notification = notificationRepository
                .findByNotificationIdAndMemberIdAndDeletedAtIsNull(notificationId, memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NOTIFICATION_NOT_FOUND));

        notification.markAsRead();
        return NotificationConverter.toNotificationItemDTO(notification);
    }

    @Transactional
    public void deleteNotification(Long memberId, Long notificationId) {

        Notification notification = notificationRepository
                .findByNotificationIdAndMemberIdAndDeletedAtIsNull(notificationId, memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NOTIFICATION_NOT_FOUND));

        notificationRepository.delete(notification);
    }

}
