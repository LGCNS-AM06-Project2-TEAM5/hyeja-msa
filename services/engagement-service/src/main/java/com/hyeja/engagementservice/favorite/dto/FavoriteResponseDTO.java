package com.hyeja.engagementservice.favorite.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hyeja.engagementservice.policy.enums.PolicyCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import com.hyeja.engagementservice.policy.enums.PolicyApplyPeriod;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

public final class FavoriteResponseDTO {

    private FavoriteResponseDTO() {
    }

    @Getter
    @Builder
    @Schema(name = "FavoriteItemDTO", description = "관심 정책 단건 응답")
    public static class FavoriteItemDTO {

        @JsonProperty("favorite_id")
        @Schema(description = "관심 정책 ID", example = "1")
        private Long favoriteId;

        @JsonProperty("policy_id")
        @Schema(description = "정책 ID", example = "R202609230001")
        private String policyId;

        @JsonProperty("policy_name")
        @Schema(description = "정책명", example = "청년 월세 지원")
        private String policyName;

        @JsonProperty("category_codes")
        @Schema(description = "정책 분류 코드 목록", example = "[\"MONTHLY_RENT\", \"PUBLIC_RENT\"]")
        private Set<PolicyCategory> categoryCodes;

        @JsonProperty("category_names")
        @Schema(description = "정책 분류 이름 목록", example = "[\"월세\", \"공공임대\"]")
        private List<String> categoryNames;

        @JsonProperty("support_content")
        @Schema(description = "지원 내용", nullable = true)
        private String supportContent;

        @JsonProperty("apply_end_date")
        @Schema(description = "신청 마감일", example = "2026-09-30", nullable = true)
        private LocalDate applyEndDate;

        @JsonProperty("apply_period_code")
        @Schema(description = "신청 기간 구분 코드", example = "0057003")
        private PolicyApplyPeriod applyPeriodCode;

        @JsonProperty("apply_url")
        @Schema(description = "신청 URL", example = "https://example.com/apply", nullable = true)
        private String applyUrl;

        @JsonProperty("created_at")
        @Schema(description = "관심 정책 등록 시각", example = "2026-09-24T10:30:00")
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @Schema(name = "FavoriteListDTO", description = "관심 정책 목록 응답")
    public static class FavoriteListDTO {

        @Schema(description = "관심 정책 목록")
        private List<FavoriteItemDTO> favorites;

        @Schema(description = "현재 페이지 번호", example = "0")
        private int page;

        @Schema(description = "페이지당 관심 정책 개수", example = "8")
        private int size;

        @Schema(description = "전체 관심 정책 개수", example = "17")
        private long totalElements;

        @Schema(description = "전체 페이지 수", example = "3")
        private int totalPages;

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        private boolean hasNext;
    }
}
