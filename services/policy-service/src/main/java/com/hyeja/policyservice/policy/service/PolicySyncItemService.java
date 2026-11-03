package com.hyeja.policyservice.policy.service;

import com.hyeja.policyservice.cardnews.entity.CardNews;
import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.event.PolicyChangedPublisher;
import com.hyeja.policyservice.policy.converter.PolicyApiConverter;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.repository.RegionRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicySyncItemService {
    private static final int CARD_BODY_MAX_LENGTH = 500;
    private static final Pattern REGION_CODE_PATTERN = Pattern.compile("(?<!\\d)\\d{5}(?!\\d)");

    private final PolicyRepository policyRepository;
    private final CardNewsRepository cardNewsRepository;
    private final PolicyApiConverter policyApiConverter;
    private final PolicyRegionRepository policyRegionRepository;
    private final RegionRepository regionRepository;
    private final PolicyChangedPublisher policyChangedPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(PolicyItem item, PolicyAiAnalysis analysis) {
        List<Region> regions = resolvePolicyRegions(item.getPolicyId(), item.getRegionCodes());
        Policy policy = policyRepository.save(policyApiConverter.convert(
                item, analysis.categories(), analysis.description(), analysis.houselessRequirement(),
                analysis.incomeCondition(), analysis.incomeMin(), analysis.incomeMax()));
        replacePolicyRegions(policy, regions);
        createMissingCardNews(policy, analysis);
        policyChangedPublisher.publish(List.of(policy.getPolicyId()));
    }

    // zipCd의 지역 코드를 전부 저장합니다. 지역이 하나도 없으면(또는 00000) 전국 정책입니다.
    private List<Region> resolvePolicyRegions(String policyId, String rawRegionCodes) {
        Set<String> regionCodes = parseRegionCodes(rawRegionCodes);
        regionCodes.remove("00000");
        return regionCodes.isEmpty() ? List.of() : resolveRegions(policyId, regionCodes);
    }

    private void replacePolicyRegions(Policy policy, List<Region> regions) {
        policyRegionRepository.deleteAllByPolicy_PolicyId(policy.getPolicyId());
        policyRegionRepository.saveAll(regions.stream()
                .map(region -> PolicyRegion.builder().policy(policy).region(region).build())
                .toList());
    }

    // REGION(시군구 256건)에서 코드를 찾습니다. REGION에는 새 행을 만들지 않습니다(회원 거주지 목록에 섞이지 않게).
    // 시·도 코드(예: 11000 서울 전체)는 그 시·도의 시군구 전체로 풀어서 저장합니다.
    private List<Region> resolveRegions(String policyId, Set<String> regionCodes) {
        Map<String, Region> resolved = new LinkedHashMap<>();
        // 예전 수집이 만든 시·도 행(예: 11000)이 REGION에 남아 있어도 그대로 쓰지 않고 아래에서 시군구로 풉니다.
        // 회원·정책이 참조하고 있을 수 있어 그 행을 지우지는 않습니다.
        regionRepository.findAllById(regionCodes).stream()
                .filter(region -> !isSidoCode(region.getRegionCode()))
                .forEach(region -> resolved.put(region.getRegionCode(), region));

        Set<String> unresolved = new LinkedHashSet<>(regionCodes);
        unresolved.removeAll(resolved.keySet());
        for (String code : List.copyOf(unresolved)) {
            if (!isSidoCode(code)) continue;
            List<Region> sigungus = regionRepository.findAllByRegionCodeStartingWith(code.substring(0, 2)).stream()
                    .filter(region -> !isSidoCode(region.getRegionCode()))
                    .toList();
            sigungus.forEach(region -> resolved.putIfAbsent(region.getRegionCode(), region));
            if (!sigungus.isEmpty()) unresolved.remove(code);
        }

        if (!unresolved.isEmpty()) {
            log.warn("REGION에 없는 정책 지역 코드는 건너뜁니다. policyId={}, codes={}", policyId, unresolved);
        }
        // 코드가 있는데 하나도 못 찾으면 전국으로 잘못 저장하지 않도록 이 정책은 저장하지 않습니다.
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("정책 지역 코드를 찾을 수 없습니다: " + unresolved);
        }
        return List.copyOf(resolved.values());
    }

    private boolean isSidoCode(String regionCode) {
        return !"00000".equals(regionCode) && regionCode.endsWith("000");
    }

    private Set<String> parseRegionCodes(String rawRegionCodes) {
        Set<String> regionCodes = new LinkedHashSet<>();
        if (rawRegionCodes == null || rawRegionCodes.isBlank()) {
            return regionCodes;
        }
        Matcher matcher = REGION_CODE_PATTERN.matcher(rawRegionCodes);
        while (matcher.find()) {
            regionCodes.add(matcher.group());
        }
        return regionCodes;
    }

    private void createMissingCardNews(Policy policy, PolicyAiAnalysis analysis) {
        saveCardIfAbsent(policy, 1L, policy.getPolicyName(),
                defaultIfBlank(analysis.description(), policy.getSupportContent()));
        saveCardIfAbsent(policy, 2L, null,
                defaultIfBlank(analysis.eligibilityDescription(), policy.getExtraQualification()));
        saveCardIfAbsent(policy, 3L,
                defaultIfBlank(analysis.benefitTitle(), "지원 혜택"),
                defaultIfBlank(analysis.benefitDescription(), policy.getSupportContent()));
        saveCardIfAbsent(policy, 4L, formatApplyPeriod(policy),
                defaultIfBlank(analysis.applicationDescription(), policy.getApplyMethod()));
    }

    private void saveCardIfAbsent(Policy policy, Long cardNo, String title, String body) {
        if (cardNewsRepository.existsByPolicy_PolicyIdAndCardNo(policy.getPolicyId(), cardNo)) {
            return;
        }
        cardNewsRepository.save(CardNews.builder()
                .policy(policy)
                .title(limit(title, 255))
                .body(limit(defaultIfBlank(body, "등록된 내용이 없습니다."), CARD_BODY_MAX_LENGTH))
                .cardNo(cardNo)
                .build());
    }

    private String formatApplyPeriod(Policy policy) {
        if (policy.getApplyStartDate() != null && policy.getApplyEndDate() != null) {
            return "%s ~ %s".formatted(policy.getApplyStartDate(), policy.getApplyEndDate());
        }
        return policy.getApplyPeriodCode() == null
                ? "신청 기간 미정" : policy.getApplyPeriodCode().getLabel();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength - 3) + "...";
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
