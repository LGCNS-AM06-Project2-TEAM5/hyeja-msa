package com.hyeja.policyservice.cardnews.dto;

import java.util.List;

public record CardNewsDetailResponseDTO(
        String policyId,
        String categoryLabel,
        Integer dDay,
        boolean isAuthenticated,
        boolean isFavorite,
        String applyUrl,
        List<CardDTO> cards
) {
    // 하트는 트랜잭션 밖에서 engagement(F4)로 확인한 뒤 컨트롤러가 채웁니다.
    public CardNewsDetailResponseDTO withFavorite(boolean favorite) {
        return new CardNewsDetailResponseDTO(policyId, categoryLabel, dDay, isAuthenticated, favorite, applyUrl, cards);
    }

    public record CardDTO(
            Long cardNewsId,
            Long cardNo,
            String title,
            List<String> badges,
            String body
    ) {
    }
}
