package com.hyeja.policyservice.internal.service;

import com.hyeja.policyservice.internal.dto.PolicyCandidateDTO;
import com.hyeja.policyservice.internal.dto.PolicyCandidateRequest;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicySort;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyCandidateService {
    private static final int SEARCH_LIMIT = 20;

    private final PolicyRepository policyRepository;
    private final PolicyRegionRepository policyRegionRepository;

    public List<PolicyCandidateDTO> findCandidates(PolicyCandidateRequest request) {
        // 1차 PolicySearchService의 후보 조회까지만 이관합니다. 자격 판정은 Recommendation 책임입니다.
        Collection<PolicyCategory> categories = request.categories().isEmpty()
                ? Set.of(PolicyCategory.values()) : request.categories();
        List<Policy> policies = policyRepository.searchActivePolicies(
                categories.contains(PolicyCategory.MONTHLY_RENT),
                categories.contains(PolicyCategory.JEONSE),
                categories.contains(PolicyCategory.PURCHASE),
                categories.contains(PolicyCategory.PUBLIC_RENT),
                categories.contains(PolicyCategory.OTHER),
                request.regionCode(), request.today(),
                PageRequest.of(0, SEARCH_LIMIT, PolicySort.DEADLINE.toSort()));
        if (policies.isEmpty()) {
            return List.of();
        }

        List<String> ids = policies.stream().map(Policy::getPolicyId).toList();
        Map<String, List<PolicyRegion>> regionsByPolicyId = policyRegionRepository
                .findAllActiveByPolicyIds(ids).stream()
                .collect(Collectors.groupingBy(link -> link.getPolicy().getPolicyId()));
        return policies.stream()
                .map(policy -> PolicyCandidateDTO.from(policy,
                        regionsByPolicyId.getOrDefault(policy.getPolicyId(), List.of())))
                .toList();
    }
}
