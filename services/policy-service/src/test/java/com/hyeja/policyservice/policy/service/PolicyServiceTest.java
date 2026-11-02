package com.hyeja.policyservice.policy.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.hyeja.policyservice.policy.converter.PolicyApiCodeConverter;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.policy.enums.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class PolicyServiceTest {
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final PolicyAiAnalyzer policyAiAnalyzer = mock(PolicyAiAnalyzer.class);
    private final PolicySyncItemService policySyncItemService = mock(PolicySyncItemService.class);
    private final PolicyService service = new PolicyService(
            restTemplate, new PolicyApiCodeConverter(), policyAiAnalyzer, policySyncItemService);
    @Test
    void doesNotSaveNonApprovedHousingPolicy() {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");

        PolicyItem rejected = new PolicyItem();
        rejected.setPolicyId("rejected-policy");
        rejected.setPolicyName("반려된 주거 정책");
        rejected.setCategory("주거");
        rejected.setApprovalStatusCode("44003");

        PolicyApiResponseDTO.Pagging pagging = new PolicyApiResponseDTO.Pagging();
        pagging.setTotCount(1);
        PolicyApiResponseDTO.ResultData result = new PolicyApiResponseDTO.ResultData();
        result.setPagging(pagging);
        result.setYouthPolicyList(List.of(rejected));
        PolicyApiResponseDTO response = new PolicyApiResponseDTO();
        response.setResult(result);

        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class))).thenReturn(response);

        assertThat(service.fetchAndSaveHousingPolicies()).isZero();
        verify(policySyncItemService, never()).save(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(policyAiAnalyzer, never()).analyze(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void savesCategoryReturnedByAiClassifier() {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");

        PolicyItem item = new PolicyItem();
        item.setPolicyId("ai-policy");
        item.setPolicyName("청년 임대주택 정책");
        item.setCategory("주거");
        item.setApprovalStatusCode("44002");

        PolicyApiResponseDTO.Pagging pagging = new PolicyApiResponseDTO.Pagging();
        pagging.setTotCount(1);
        PolicyApiResponseDTO.ResultData result = new PolicyApiResponseDTO.ResultData();
        result.setPagging(pagging);
        result.setYouthPolicyList(List.of(item));
        PolicyApiResponseDTO response = new PolicyApiResponseDTO();
        response.setResult(result);

        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class))).thenReturn(response);
        PolicyAiAnalysis analysis = new PolicyAiAnalysis(
                "청년에게 공공임대주택 입주 기회를 제공합니다.",
                Set.of(PolicyCategory.PUBLIC_RENT), 0.95, "공공임대주택 입주 정책",
                PolicyHouselessRequirement.REQUIRED, 0.91, "무주택 세대구성원 조건이 명시됨",
                PolicyIncomeCondition.COMPARABLE, null, 50_000_000,
                0.90, "개인 연소득 5천만원 이하");
        when(policyAiAnalyzer.analyze(item)).thenReturn(analysis);

        assertThat(service.fetchAndSaveHousingPolicies()).isEqualTo(1);
        verify(policySyncItemService).save(item, analysis);
    }

    @Test
    void skipsFailedItemAndContinuesWithNextPolicy() {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");

        PolicyItem failed = approvedHousingPolicy("failed-policy");
        PolicyItem succeeded = approvedHousingPolicy("succeeded-policy");
        PolicyApiResponseDTO.Pagging pagging = new PolicyApiResponseDTO.Pagging();
        pagging.setTotCount(2);
        PolicyApiResponseDTO.ResultData result = new PolicyApiResponseDTO.ResultData();
        result.setPagging(pagging);
        result.setYouthPolicyList(List.of(failed, succeeded));
        PolicyApiResponseDTO response = new PolicyApiResponseDTO();
        response.setResult(result);

        PolicyAiAnalysis analysis = new PolicyAiAnalysis(
                "기타 주거 지원 정책입니다.",
                Set.of(PolicyCategory.OTHER), 0.8, "기타 주거 정책",
                PolicyHouselessRequirement.UNKNOWN, 0.5, "확인 필요",
                PolicyIncomeCondition.UNKNOWN, null, null,
                0.5, "확인 필요");
        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class))).thenReturn(response);
        when(policyAiAnalyzer.analyze(failed)).thenThrow(new RuntimeException("timeout"));
        when(policyAiAnalyzer.analyze(succeeded)).thenReturn(analysis);

        assertThat(service.fetchAndSaveHousingPolicies()).isEqualTo(1);
        verify(policySyncItemService).save(succeeded, analysis);
        verify(policySyncItemService, never()).save(
                org.mockito.ArgumentMatchers.eq(failed), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loadsAtMostConfiguredPages() {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");

        PolicyItem nonHousing = new PolicyItem();
        nonHousing.setPolicyId("non-housing");
        nonHousing.setCategory("일자리");

        PolicyApiResponseDTO.Pagging pagging = new PolicyApiResponseDTO.Pagging();
        pagging.setTotCount(10_000);
        PolicyApiResponseDTO.ResultData result = new PolicyApiResponseDTO.ResultData();
        result.setPagging(pagging);
        result.setYouthPolicyList(List.of(nonHousing));
        PolicyApiResponseDTO response = new PolicyApiResponseDTO();
        response.setResult(result);

        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class))).thenReturn(response);

        assertThat(service.fetchAndSaveHousingPolicies()).isZero();
        int configuredMaxPages = (int) ReflectionTestUtils.getField(
                PolicyService.class, "MAX_PAGES");
        verify(restTemplate, times(configuredMaxPages)).getForObject(
                org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class));
    }

    @Test
    void savesPolicyWhoseCategoryListContainsHousing() {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");
        PolicyItem duplicated = approvedHousingPolicy("duplicated-housing");
        duplicated.setCategory("주거,주거");
        PolicyItem other = approvedHousingPolicy("not-housing");
        other.setCategory("일자리,복지문화");
        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class)))
                .thenReturn(page(2, duplicated, other));

        assertThat(service.fetchAndSaveHousingPolicies()).isEqualTo(1);
        verify(policySyncItemService).save(org.mockito.ArgumentMatchers.eq(duplicated), org.mockito.ArgumentMatchers.any());
        verify(policySyncItemService, never()).save(org.mockito.ArgumentMatchers.eq(other), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retriesFailedPageOnceAndContinues() {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");
        PolicyItem first = approvedHousingPolicy("page-1");
        PolicyItem second = approvedHousingPolicy("page-2");
        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class)))
                .thenReturn(page(200, first))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection reset"))
                .thenReturn(page(200, second));

        assertThat(service.fetchAndSaveHousingPolicies()).isEqualTo(2);
        verify(policySyncItemService).save(org.mockito.ArgumentMatchers.eq(second), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void stopsAndReportsStoppedPageWhenPageFailsTwice() {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");
        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class)))
                .thenReturn(page(300, approvedHousingPolicy("page-1")))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection reset"));

        assertThatThrownBy(() -> service.fetchAndSaveHousingPolicies())
                .isInstanceOf(com.hyeja.policyservice.global.exception.GeneralException.class)
                .satisfies(exception -> {
                    com.hyeja.policyservice.global.exception.GeneralException error =
                            (com.hyeja.policyservice.global.exception.GeneralException) exception;
                    assertThat(error.getCode()).isEqualTo(com.hyeja.policyservice.global.apiPayload.status.ErrorStatus.POLICY_SYNC_STOPPED);
                    assertThat(error.getResult()).isEqualTo(java.util.Map.of("stoppedPage", 2, "savedCount", 1));
                });
        // 1페이지에서 저장한 정책은 남습니다.
        verify(policySyncItemService).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        // 1페이지 1번 + 2페이지 2번(재시도 포함). 3페이지는 요청하지 않습니다.
        verify(restTemplate, times(3)).getForObject(
                org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class));
    }

    @Test
    void reportsStopWhenFirstPageFails() {
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");
        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class)))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.fetchAndSaveHousingPolicies())
                .extracting("result")
                .isEqualTo(java.util.Map.of("stoppedPage", 1, "savedCount", 0));
    }
    private PolicyApiResponseDTO page(int totCount, PolicyItem... items) {
        PolicyApiResponseDTO.Pagging pagging = new PolicyApiResponseDTO.Pagging();
        pagging.setTotCount(totCount);
        PolicyApiResponseDTO.ResultData result = new PolicyApiResponseDTO.ResultData();
        result.setPagging(pagging);
        result.setYouthPolicyList(List.of(items));
        PolicyApiResponseDTO response = new PolicyApiResponseDTO();
        response.setResult(result);
        return response;
    }
    private PolicyItem approvedHousingPolicy(String policyId) {
        PolicyItem item = new PolicyItem();
        item.setPolicyId(policyId);
        item.setPolicyName(policyId);
        item.setCategory("주거");
        item.setApprovalStatusCode("44002");
        return item;
    }
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void doesNotLogApiKeyWhenRequestExceptionContainsUrl(CapturedOutput output) {
        ReflectionTestUtils.setField(service, "apiKey", "sensitive-test-key");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.com/policies");
        when(restTemplate.getForObject(org.mockito.ArgumentMatchers.any(java.net.URI.class),
                org.mockito.ArgumentMatchers.eq(PolicyApiResponseDTO.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException(
                        "GET https://example.com/policies?apiKeyNm=sensitive-test-key",
                        new java.io.IOException("connection failed: sensitive-test-key")));
        assertThatThrownBy(() -> service.fetchAndSaveHousingPolicies())
                .isInstanceOf(com.hyeja.policyservice.global.exception.GeneralException.class);
        assertThat(output.getAll()).doesNotContain("sensitive-test-key");
    }

}
