package com.hyeja.recommendationservice.favorite.client;

import com.hyeja.recommendationservice.favorite.dto.FavoriteActivePolicyIdsRequest;
import com.hyeja.recommendationservice.global.apiPayload.ApiResponse;
import com.hyeja.recommendationservice.global.apiPayload.status.SuccessStatus;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// F3 Adapter: 검색 코어의 FavoriteClient 호출을 engagement-service 관심 여부 API로 보내고 응답을 Set으로 바꿉니다.
// 모든 실패는 예외로 던지며, 관심 표시 없이 응답하는 처리는 PolicySearchService.findFavoriteIds가 맡습니다.
@Component
@RequiredArgsConstructor
public class FavoriteClientAdapter implements FavoriteClient {
    private final FavoriteFeignClient favoriteFeignClient;

    @Override
    public Set<String> findActivePolicyIds(Long memberId, List<String> policyIds) {
        ApiResponse<List<String>> response = favoriteFeignClient.findActivePolicyIds(
                new FavoriteActivePolicyIdsRequest(memberId, policyIds));
        // 204·빈 본문·실패 형식·result 누락은 관심 없음(result: [])과 구분해 장애로 처리합니다.
        if (response == null || !Boolean.TRUE.equals(response.getIsSuccess())
                || !SuccessStatus.OK.getCode().equals(response.getCode()) || response.getResult() == null) {
            throw new IllegalStateException("engagement-service 관심 여부 응답이 정상 형식이 아닙니다.");
        }
        // 응답 순서는 계약상 보장되지 않으며 검색 코어는 포함 여부만 봅니다.
        return Set.copyOf(response.getResult());
    }
}
