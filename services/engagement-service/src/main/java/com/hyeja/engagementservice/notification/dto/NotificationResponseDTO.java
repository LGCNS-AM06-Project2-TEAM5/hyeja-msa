package com.hyeja.engagementservice.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

public final class NotificationResponseDTO {

    private NotificationResponseDTO() {
    }

    @Getter
    @Builder
    @Schema(name = "NotificationItemDTO", description = "알림 단건 응답")
    public static class NotificationItemDTO {

        @JsonProperty("notification_id")
        @Schema(description = "알림 ID", example = "1")
        private Long notificationId;

        @JsonProperty("member_id")
        @Schema(description = "회원 ID", example = "1")
        private Long memberId;

        @JsonProperty("policy_id")
        @Schema(description = "정책 ID", example = "R202609230001")
        private String policyId;

        @JsonProperty("policy_name")
        @Schema(description = "정책명", example = "서울시 청년 월세 지원")
        private String policyName;

        @Schema(description = "알림 내용", example = "관심 정책의 신청 마감이 일주일 남았어요")
        private String content;

        @JsonProperty("read_yn")
        @Schema(description = "읽음 여부", example = "false")
        private Boolean readYn;

        @JsonProperty("apply_end_date")
        @Schema(description = "알림 생성 시 저장한 정책 신청 마감일", example = "2026-09-30")
        private LocalDate applyEndDate;

        @JsonProperty("created_at")
        @Schema(description = "알림 생성 시각", example = "2026-09-24T10:30:00")
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @Schema(name = "NotificationListDTO", description = "알림 페이지 조회 응답")
    public static class NotificationListDTO {

        @Schema(description = "현재 페이지의 알림 목록")
        private List<NotificationItemDTO> notifications;

        @JsonProperty("unread_count")
        @Schema(description = "삭제되지 않은 전체 알림 중 안 읽은 알림 개수", example = "3")
        private long unreadCount;

        @Schema(description = "현재 페이지 번호", example = "0")
        private int page;

        @Schema(description = "페이지당 알림 개수", example = "8")
        private int size;

        @Schema(description = "전체 알림 개수", example = "24")
        private long totalElements;

        @Schema(description = "전체 페이지 수", example = "3")
        private int totalPages;

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        private boolean hasNext;
    }
}
