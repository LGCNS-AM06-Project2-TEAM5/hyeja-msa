package com.hyeja.recommendationservice.favorite.dto;

import java.util.List;

// F3 요청(POST /internal/favorites/active-policy-ids). policyIds는 검색 후보 순서 그대로이며 최대 20건입니다.
public record FavoriteActivePolicyIdsRequest(
        Long memberId,
        List<String> policyIds) {
}
