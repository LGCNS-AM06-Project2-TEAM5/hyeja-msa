package com.hyeja.recommendationservice.policy.client;

import com.hyeja.recommendationservice.global.apiPayload.ApiResponse;
import com.hyeja.recommendationservice.policy.dto.EligibilityPolicyDTO;
import com.hyeja.recommendationservice.policy.dto.PolicyCandidateDTO;
import com.hyeja.recommendationservice.policy.dto.PolicyCandidateRequest;
import com.hyeja.recommendationservice.policy.dto.SearchPolicyDTO;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import com.hyeja.recommendationservice.region.dto.RegionDTO;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// F2 Adapter: 검색 코어의 PolicyClient 호출을 policy-service 정책 후보 API로 보내고 응답을 SearchPolicyDTO로 바꿉니다.
// HTTP 오류·연결 실패·Timeout·역직렬화 실패는 FeignException 그대로 전파해 검색 실패(SERVER_001)가 되게 합니다.
// 회로가 열려 있으면 호출하지 않고 CallNotPermittedException을 던지며, 빈 후보로 바꾸지 않습니다.
@Component
@RequiredArgsConstructor
public class PolicyClientAdapter implements PolicyClient {
    static final String CIRCUIT_BREAKER = "recPolicyCandidates";

    private final PolicyCandidateFeignClient policyCandidateFeignClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    // limit은 요청에 넣지 않습니다. F2가 정렬 후 최대 20건(PolicySearchService.SEARCH_LIMIT와 같은 값)으로 고정합니다.
    @Override
    public List<SearchPolicyDTO> searchActivePolicies(
            Set<PolicyCategory> categories, String regionCode, LocalDate today, int limit) {
        PolicyCandidateRequest request =
                new PolicyCandidateRequest(categories.stream().sorted().toList(), regionCode, today);
        ApiResponse<List<PolicyCandidateDTO>> response = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER)
                .executeSupplier(() -> policyCandidateFeignClient.findCandidates(request));
        // 204·빈 본문·result 누락은 정상 후보 없음(result: [])과 구분해 검색 실패로 처리합니다.
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("policy-service 정책 후보 응답에 result가 없습니다.");
        }
        return response.getResult().stream().map(PolicyClientAdapter::toSearchPolicy).toList();
    }

    // 1차와 같은 순서로 맞춥니다. 카테고리는 이름 오름차순(PolicyCategoryConverter), 지역은 코드 오름차순(PolicyRegionRepository).
    private static SearchPolicyDTO toSearchPolicy(PolicyCandidateDTO candidate) {
        return new SearchPolicyDTO(
                candidate.policyId(),
                candidate.policyName(),
                candidate.categories() == null ? null : candidate.categories().stream()
                        .sorted(Comparator.comparing(Enum::name))
                        .collect(Collectors.toCollection(LinkedHashSet::new)),
                candidate.applyEndDate(),
                candidate.applyPeriodCode(),
                candidate.extraQualification(),
                EligibilityPolicyDTO.builder()
                        .ageLimitYn(candidate.ageLimitYn())
                        .minAge(candidate.minAge())
                        .maxAge(candidate.maxAge())
                        .incomeConditionCode(candidate.incomeConditionCode())
                        .incomeMin(candidate.incomeMin())
                        .incomeMax(candidate.incomeMax())
                        .incomeEtc(candidate.incomeEtc())
                        .employmentCodes(candidate.employmentCodes())
                        .houselessRequirement(candidate.houselessRequirement())
                        .build(),
                candidate.regions() == null ? null : candidate.regions().stream()
                        .sorted(Comparator.comparing(PolicyCandidateDTO.Region::regionCode))
                        .map(region -> RegionDTO.builder()
                                .regionCode(region.regionCode())
                                .sigunguName(region.sigunguName())
                                .build())
                        .toList());
    }
}
