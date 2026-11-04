package com.hyeja.policyservice.cardnews.dto;

import java.time.LocalDate;

// 로그인 회원 홈 카드뉴스 한 건(1차 MemberCardNewsResponseDTO와 같은 JSON).
public record MemberCardNewsResponseDTO(
        String policyId,
        String policyName,
        String description,
        LocalDate applyEndDate
) {
}
