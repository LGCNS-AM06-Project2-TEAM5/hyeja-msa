package com.hyeja.policyservice.policy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

// 로그인 회원 정책 목록(/housing/me) 응답. 1차 PolicyResponseDTO와 같은 JSON(snake_case, favorite_yn 포함)입니다.
public final class PolicyMemberResponseDTO {

    private PolicyMemberResponseDTO() {
    }

    @Getter
    @Builder
    public static class PolicyRegionItemDTO {

        @JsonProperty("region_code")
        private String regionCode;

        @JsonProperty("region_name")
        private String regionName;
    }

    @Getter
    @Builder
    public static class PolicyListItemDTO {

        @JsonProperty("policy_id")
        private String policyId;

        @JsonProperty("policy_name")
        private String policyName;

        @JsonProperty("category_codes")
        private Set<PolicyCategory> categoryCodes;

        @JsonProperty("category_names")
        private List<String> categoryNames;
        private List<PolicyRegionItemDTO> regions;
        private boolean nationwide;

        // regions가 수백 개일 수 있어(전국 대상 정책) 화면의 지역 칸에는 이 문구를 씁니다.
        @JsonProperty("region_summary")
        private String regionSummary;

        @JsonProperty("apply_end_date")
        private LocalDate applyEndDate;

        @JsonProperty("apply_period_code")
        private PolicyApplyPeriod applyPeriodCode;

        @JsonProperty("d_day")
        private Integer dDay;

        @JsonProperty("d_day")
        public Integer getDDay() {
            return dDay;
        }

        @JsonProperty("favorite_yn")
        private boolean favoriteYn;
    }

    @Getter
    @Builder
    public static class PolicyListDTO {
        private List<PolicyListItemDTO> policies;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
    }
}
