package com.hyeja.policyservice.client.engagement;

import com.hyeja.policyservice.client.engagement.dto.ActivePolicyIdsRequest;
import com.hyeja.policyservice.client.engagement.dto.EngagementApiResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 로그인 회원이 관심 등록한 정책 ID를 engagement(F4)에서 가져옵니다. 1차 FavoriteRepository.findActivePolicyIds 자리입니다.
// engagement가 한 번에 20개까지 받으므로 나눠서 묻고, 실패하면 계약(docs/api F4)대로 하트 없이(빈 집합) 응답합니다.
// 청크마다 policyEngagementFavorites 회로를 거치며, 회로가 열려 호출하지 않은 경우도 실패와 같이 하트 없이 응답합니다.
@Slf4j
@Component
@RequiredArgsConstructor
public class FavoriteReader {

    static final int MAX_POLICY_IDS = 20;
    static final String CIRCUIT_BREAKER = "policyEngagementFavorites";

    private final EngagementClient engagementClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public Set<String> findFavoritePolicyIds(Long memberId, List<String> policyIds) {
        if (memberId == null || policyIds.isEmpty()) {
            return Set.of();
        }
        List<String> distinct = new ArrayList<>(new LinkedHashSet<>(policyIds));
        Set<String> favorites = new HashSet<>();
        for (int from = 0; from < distinct.size(); from += MAX_POLICY_IDS) {
            List<String> chunk = distinct.subList(from, Math.min(from + MAX_POLICY_IDS, distinct.size()));
            EngagementApiResponse<List<String>> response;
            ActivePolicyIdsRequest request = new ActivePolicyIdsRequest(memberId, chunk);
            try {
                response = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER)
                        .executeSupplier(() -> engagementClient.findActivePolicyIds(request));
            } catch (FeignException e) {
                log.warn("관심 여부를 불러오지 못해 하트 없이 응답합니다. memberId={}, status={}, message={}",
                        memberId, e.status(), e.getMessage());
                return Set.of();
            } catch (CallNotPermittedException e) {
                log.warn("engagement 회로가 열려 있어 하트 없이 응답합니다. memberId={}", memberId);
                return Set.of();
            }
            if (response == null || !Boolean.TRUE.equals(response.isSuccess()) || response.result() == null) {
                log.warn("관심 여부 응답이 비어 하트 없이 응답합니다. memberId={}", memberId);
                return Set.of();
            }
            favorites.addAll(response.result());
        }
        return favorites;
    }

    public boolean isFavorite(Long memberId, String policyId) {
        return findFavoritePolicyIds(memberId, List.of(policyId)).contains(policyId);
    }
}
