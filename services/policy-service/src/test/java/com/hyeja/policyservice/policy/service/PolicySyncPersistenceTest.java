package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hyeja.policyservice.cardnews.entity.CardNews;
import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.repository.RegionRepository;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PolicySyncPersistenceTest {
    @Autowired private PolicyService service;
    @Autowired private PolicySyncItemService itemService;
    @Autowired private PolicyRepository policies;
    @Autowired private PolicyRegionRepository links;
    @Autowired private RegionRepository regions;
    @MockitoSpyBean private CardNewsRepository cards;
    @MockitoBean private RestTemplate restTemplate;
    @MockitoBean private PolicyAiAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        cards.deleteAll();
        links.deleteAll();
        policies.deleteAll();
        regions.deleteAll();
        regions.saveAll(List.of(
                Region.builder().regionCode("11110").sigunguName("서울특별시 종로구").build(),
                Region.builder().regionCode("11680").sigunguName("서울특별시 강남구").build()));
    }

    @Test
    void resyncUpdatesPolicyAndRegionsWithoutDuplicatingCards() {
        PolicyItem item = item("sync-1", "11110");
        itemService.save(item, analysis());
        var originalCards = cards.findAll().stream().map(CardNews::getCardNewsId).sorted().toList();
        assertThat(originalCards).hasSize(4);

        item.setPolicyName("갱신된 정책");
        item.setRegionCodes("11110,11680");
        itemService.save(item, analysis());
        assertThat(policies.count()).isEqualTo(1);
        assertThat(policies.findById("sync-1").orElseThrow().getPolicyName()).isEqualTo("갱신된 정책");
        assertThat(links.findAllByPolicy_PolicyId("sync-1")).hasSize(2);
        assertThat(cards.findAll().stream().map(CardNews::getCardNewsId).sorted().toList())
                .isEqualTo(originalCards);

        item.setRegionCodes("11680");
        itemService.save(item, analysis());
        assertThat(links.findAllByPolicy_PolicyId("sync-1"))
                .extracting(value -> value.getId().getRegionCode()).containsExactly("11680");
        assertThat(cards.count()).isEqualTo(4);
    }

    @Test
    void laterPageFailureDoesNotRollBackPreviouslyCommittedItem() {
        when(restTemplate.getForObject(any(URI.class), eq(PolicyApiResponseDTO.class)))
                .thenReturn(page(200, item("committed", "11110")))
                .thenThrow(new ResourceAccessException("connection reset"));
        when(analyzer.analyze(any())).thenReturn(analysis());

        assertThatThrownBy(() -> service.fetchAndSaveHousingPolicies())
                .isInstanceOf(GeneralException.class)
                .extracting("result")
                .isEqualTo(java.util.Map.of("stoppedPage", 2, "savedCount", 1));
        assertThat(policies.existsById("committed")).isTrue();
        assertThat(links.count()).isEqualTo(1);
        assertThat(cards.count()).isEqualTo(4);
    }

    @Test
    void failedCardSaveRollsBackOnlyThatItemAndCollectionContinues() {
        doThrow(new IllegalStateException("simulated card storage failure"))
                .when(cards).save(argThat(card -> card != null
                        && "failed".equals(card.getPolicy().getPolicyId())));
        when(restTemplate.getForObject(any(URI.class), eq(PolicyApiResponseDTO.class)))
                .thenReturn(page(2, item("failed", "11110"), item("success", "11680")));
        when(analyzer.analyze(any())).thenReturn(analysis());

        assertThat(service.fetchAndSaveHousingPolicies()).isEqualTo(1);
        assertThat(policies.existsById("failed")).isFalse();
        assertThat(policies.existsById("success")).isTrue();
        assertThat(links.count()).isEqualTo(1);
        assertThat(cards.count()).isEqualTo(4);
    }

    private PolicyItem item(String id, String regionCodes) {
        PolicyItem item = new PolicyItem();
        item.setPolicyId(id);
        item.setPolicyName("주거 지원");
        item.setCategory("주거");
        item.setApprovalStatusCode("44002");
        item.setApplyPeriodCode("57002");
        item.setRegionCodes(regionCodes);
        return item;
    }

    private PolicyAiAnalysis analysis() {
        return new PolicyAiAnalysis("요약입니다.", Set.of(PolicyCategory.OTHER), 0.8, "주거",
                PolicyHouselessRequirement.UNKNOWN, 0.5, "미상",
                PolicyIncomeCondition.UNKNOWN, null, null, 0.5, "미상",
                "대상입니다.", "지원 혜택", "지원합니다.", "신청합니다.");
    }

    private PolicyApiResponseDTO page(int total, PolicyItem... items) {
        var paging = new PolicyApiResponseDTO.Pagging();
        paging.setTotCount(total);
        var result = new PolicyApiResponseDTO.ResultData();
        result.setPagging(paging);
        result.setYouthPolicyList(List.of(items));
        var response = new PolicyApiResponseDTO();
        response.setResult(result);
        return response;
    }
}
