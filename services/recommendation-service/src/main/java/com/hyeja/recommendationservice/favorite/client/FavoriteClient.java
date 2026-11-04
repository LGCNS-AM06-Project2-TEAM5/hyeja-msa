package com.hyeja.recommendationservice.favorite.client;

import java.util.List;
import java.util.Set;

// F3 (recommendation → engagement). 구현은 Feign 연동 단계에서 추가합니다.
public interface FavoriteClient {

    // 1차 FavoriteRepository.findActivePolicyIds처럼 policyIds 중 삭제되지 않은 관심 정책 ID만 돌려줍니다.
    Set<String> findActivePolicyIds(Long memberId, List<String> policyIds);
}
