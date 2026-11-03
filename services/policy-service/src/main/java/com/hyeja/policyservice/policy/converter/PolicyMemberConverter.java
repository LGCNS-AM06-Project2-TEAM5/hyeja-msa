package com.hyeja.policyservice.policy.converter;

import com.hyeja.policyservice.policy.dto.PolicyMemberResponseDTO.PolicyListDTO;
import com.hyeja.policyservice.policy.dto.PolicyMemberResponseDTO.PolicyListItemDTO;
import com.hyeja.policyservice.policy.dto.PolicyMemberResponseDTO.PolicyRegionItemDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.region.converter.RegionConverter;
import com.hyeja.policyservice.region.entity.Region;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import org.springframework.data.domain.Page;

// 1차 PolicyConverter 그대로입니다(회원 목록 응답 변환).
public final class PolicyMemberConverter {

    private PolicyMemberConverter() {
    }

    public static PolicyListDTO toPolicyListDTO(
            Page<Policy> policyPage,
            Map<String, List<Region>> regionsByPolicyId,
            Set<String> favoritePolicyIds,
            LocalDate today
    ) {
        return PolicyListDTO.builder()
                .policies(policyPage.getContent().stream()
                        .map(policy -> toPolicyListItemDTO(
                                policy,
                                regionsByPolicyId.getOrDefault(policy.getPolicyId(), List.of()),
                                favoritePolicyIds.contains(policy.getPolicyId()),
                                today
                        ))
                        .toList())
                .page(policyPage.getNumber())
                .size(policyPage.getSize())
                .totalElements(policyPage.getTotalElements())
                .totalPages(policyPage.getTotalPages())
                .hasNext(policyPage.hasNext())
                .build();
    }

    private static PolicyListItemDTO toPolicyListItemDTO(
            Policy policy,
            List<Region> regions,
            boolean favoriteYn,
            LocalDate today
    ) {
        return PolicyListItemDTO.builder()
                .policyId(policy.getPolicyId())
                .policyName(policy.getPolicyName())
                .categoryCodes(policy.getCategories())
                .categoryNames(policy.getCategories().stream()
                        .sorted(Comparator.comparing(Enum::name))
                        .map(com.hyeja.policyservice.policy.enums.PolicyCategory::getLabel)
                        .toList())
                .regions(regions.stream()
                        .map(region -> PolicyRegionItemDTO.builder()
                                .regionCode(region.getRegionCode())
                                .regionName(region.getSigunguName())
                                .build())
                        .toList())
                .nationwide(regions.isEmpty())
                .regionSummary(RegionConverter.summarize(regions))
                .applyEndDate(policy.getApplyEndDate())
                .applyPeriodCode(policy.getApplyPeriodCode())
                .dDay(policy.getApplyEndDate() == null
                        ? null : Math.toIntExact(ChronoUnit.DAYS.between(today, policy.getApplyEndDate())))
                .favoriteYn(favoriteYn)
                .build();
    }
}
