package com.hyeja.policyservice.policy.converter;

import com.hyeja.policyservice.policy.dto.PolicyGuestResponseDTO.PolicyListDTO;
import com.hyeja.policyservice.policy.dto.PolicyGuestResponseDTO.PolicyListItemDTO;
import com.hyeja.policyservice.policy.dto.PolicyGuestResponseDTO.PolicyRegionItemDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.region.entity.Region;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public final class PolicyGuestConverter {

    private PolicyGuestConverter() {
    }

    public static PolicyListDTO toPolicyListDTO(
            Page<Policy> policyPage,
            Map<String, List<Region>> regionsByPolicyId,
            LocalDate today
    ) {
        return PolicyListDTO.builder()
                .policies(policyPage.getContent().stream()
                        .map(policy -> toPolicyListItemDTO(
                                policy,
                                regionsByPolicyId.getOrDefault(policy.getPolicyId(), List.of()),
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
                .regionSummary(summarizeRegions(regions))
                .applyEndDate(policy.getApplyEndDate())
                .applyPeriodCode(policy.getApplyPeriodCode())
                .dDay(policy.getApplyEndDate() == null
                        ? null
                        : Math.toIntExact(ChronoUnit.DAYS.between(today, policy.getApplyEndDate())))
                .build();
    }

    // 원본 RegionConverter.summarize와 동일한 규칙입니다.
    private static String summarizeRegions(List<Region> regions) {
        if (regions.isEmpty()) {
            return "전국";
        }
        String first = regions.get(0).getSigunguName();
        return regions.size() == 1 ? first : "%s 외 %d곳".formatted(first, regions.size() - 1);
    }
}
