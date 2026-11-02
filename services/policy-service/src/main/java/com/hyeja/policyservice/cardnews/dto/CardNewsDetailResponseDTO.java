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
    public record CardDTO(
            Long cardNewsId,
            Long cardNo,
            String title,
            List<String> badges,
            String body
    ) {
    }
}
