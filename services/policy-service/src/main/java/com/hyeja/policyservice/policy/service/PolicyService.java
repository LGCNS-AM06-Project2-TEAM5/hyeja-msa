package com.hyeja.policyservice.policy.service;

import com.hyeja.policyservice.policy.converter.PolicyApiCodeConverter;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

// 수집·조회 책임만 둡니다. 회원 기능은 별도 서비스에서 연결합니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyService {
    private static final String HOUSING_CATEGORY = "주거";
    private static final int PAGE_SIZE = 100;
    // totCount가 비정상인 경우 무한 수집을 막는 원본 상한입니다.
    private static final int MAX_PAGES = 50;

    private final RestTemplate restTemplate;
    private final PolicyApiCodeConverter policyApiCodeConverter;
    private final PolicyAiAnalyzer policyAiAnalyzer;
    private final PolicySyncItemService policySyncItemService;

    @Value("${youth.api.key}")
    private String apiKey;

    @Value("${youth.api.url}")
    private String apiUrl;

    public int fetchAndSaveHousingPolicies() {
        int pageNumber = 1;
        int totalCount = Integer.MAX_VALUE;
        int processedCount = 0;
        int failedCount = 0;

        do {
            PolicyApiResponseDTO response = requestPageWithRetry(pageNumber);
            if (response == null || response.getResult() == null) {
                // 끝까지 못 가고 멈춘 것을 "완료"로 응답하지 않도록 알립니다. 이미 저장한 정책은 항목마다 따로 저장돼 남습니다.
                log.error("온통청년 API {}페이지 요청 실패로 수집 중단 - 저장 {}건", pageNumber, processedCount);
                throw new GeneralException(ErrorStatus.POLICY_SYNC_STOPPED,
                        Map.of("stoppedPage", pageNumber, "savedCount", processedCount));
            }
            if (response.getResult().getPagging() != null) {
                totalCount = response.getResult().getPagging().getTotCount();
            }
            List<PolicyItem> items = response.getResult().getYouthPolicyList();
            if (items == null || items.isEmpty()) break;

            int housingCount = 0;
            for (PolicyItem item : items) {
                if (!isSavableHousingPolicy(item)) continue;
                housingCount++;
                try {
                    PolicyAiAnalysis analysis = policyAiAnalyzer.analyze(item);
                    policySyncItemService.save(item, analysis);
                    processedCount++;
                } catch (Exception exception) {
                    failedCount++;
                    log.error("정책 동기화 항목 처리 실패 - page={}, policyId={}, policyName={}",
                            pageNumber, item.getPolicyId(), item.getPolicyName(), exception);
                }
            }
            log.info("온통청년 {}페이지 - totCount={}, 주거 정책 {}건", pageNumber, totalCount, housingCount);
            pageNumber++;
        } while (pageNumber <= MAX_PAGES
                && (long) (pageNumber - 1) * PAGE_SIZE < totalCount);

        log.info("온통청년 정책 적재 완료 - maxPages={}, successCount={}, failureCount={}",
                MAX_PAGES, processedCount, failedCount);
        return processedCount;
    }

    // 대분류(lclsfNm)가 "주거,주거"처럼 쉼표로 여러 개 올 때가 있어, 그중 하나라도 주거면 저장합니다.
    // (2026-09-28 실측: "청년월세 지원" 등 2건이 이 형태라 수집에서 빠지고 있었음)
    private boolean isSavableHousingPolicy(PolicyItem item) {
        return item.getCategory() != null
                && Arrays.stream(item.getCategory().split(","))
                        .map(String::trim)
                        .anyMatch(HOUSING_CATEGORY::equals)
                && trimToNull(item.getPolicyId()) != null
                && policyApiCodeConverter.isApproved(item.getApprovalStatusCode());
    }

    // 온통청년 서버가 뒤쪽 페이지에서 연결을 끊는 경우가 있어 한 번 더 시도합니다.
    // 그래도 실패하면 null을 돌려, 호출한 쪽이 수집 중단(POLICY_002)으로 응답합니다.
    private PolicyApiResponseDTO requestPageWithRetry(int pageNumber) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return requestPage(pageNumber);
            } catch (RestClientException exception) {
                // 예외·원인 메시지에 API 키가 담긴 URL이 있을 수 있어 예외 종류만 남깁니다.
                log.warn("온통청년 API {}페이지 요청 실패 ({}번째 시도) - {}", pageNumber, attempt,
                        exception.getClass().getSimpleName());
            }
        }
        return null;
    }

    private PolicyApiResponseDTO requestPage(int pageNumber) {
        URI uri = UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("apiKeyNm", apiKey)
                .queryParam("pageNum", pageNumber)
                .queryParam("pageSize", PAGE_SIZE)
                .queryParam("rtnType", "json")
                .encode()
                .build()
                .toUri();
        log.info("온통청년 정책 API {}페이지 요청", pageNumber);
        return restTemplate.getForObject(uri, PolicyApiResponseDTO.class);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }


}
