package com.hyeja.engagementservice.notification.converter;

import com.hyeja.engagementservice.notification.dto.NotificationResponseDTO.NotificationItemDTO;
import com.hyeja.engagementservice.notification.dto.NotificationResponseDTO.NotificationListDTO;
import com.hyeja.engagementservice.notification.entity.Notification;
import org.springframework.data.domain.Page;

public final class NotificationConverter {

    private static final String DEADLINE_NOTIFICATION_CONTENT =
            "관심 정책의 신청 마감이 일주일 남았어요";

    private NotificationConverter() {
    }

    public static NotificationItemDTO toNotificationItemDTO(Notification notification) {
        return NotificationItemDTO.builder()
                .notificationId(notification.getNotificationId())
                .memberId(notification.getMemberId())
                .policyId(notification.getPolicy().getPolicyId())
                .policyName(notification.getPolicy().getPolicyName())
                .content(DEADLINE_NOTIFICATION_CONTENT)
                .readYn(notification.getReadYn())
                .applyEndDate(notification.getDeadlineDate())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    public static NotificationListDTO toNotificationListDTO(
            Page<Notification> notificationPage,
            long unreadCount
    ) {
        return NotificationListDTO.builder()
                .notifications(notificationPage.getContent().stream()
                        .map(NotificationConverter::toNotificationItemDTO)
                        .toList())
                .unreadCount(unreadCount)
                .page(notificationPage.getNumber())
                .size(notificationPage.getSize())
                .totalElements(notificationPage.getTotalElements())
                .totalPages(notificationPage.getTotalPages())
                .hasNext(notificationPage.hasNext())
                .build();
    }
}
