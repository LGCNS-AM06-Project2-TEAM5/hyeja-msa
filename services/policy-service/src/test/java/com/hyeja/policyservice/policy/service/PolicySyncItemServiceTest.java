package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hyeja.policyservice.cardnews.entity.CardNews;
import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.policy.converter.PolicyApiCodeConverter;
import com.hyeja.policyservice.policy.converter.PolicyApiConverter;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.repository.RegionRepository;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class PolicySyncItemServiceTest {
    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final CardNewsRepository cardNewsRepository = mock(CardNewsRepository.class);
    private final PolicyApiConverter policyApiConverter =
            new PolicyApiConverter(new PolicyApiCodeConverter());
    private final PolicyRegionRepository policyRegionRepository = mock(PolicyRegionRepository.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final PolicySyncItemService service = new PolicySyncItemService(
            policyRepository, cardNewsRepository, policyApiConverter,
            policyRegionRepository, regionRepository);

    @Test
    void savesPolicyAndInitialCardNewsTogether() {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("policy-1");
        item.setPolicyName("청년 주거 정책");
        item.setSupportContent("지원 내용");
        item.setRegionCodes("11110");
        item.setApplyPeriodCode("57002");
        PolicyAiAnalysis analysis = new PolicyAiAnalysis(
                "청년의 주거비를 지원합니다.",
                java.util.Set.of(PolicyCategory.OTHER), 0.8, "기타",
                com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement.UNKNOWN,
                0.5, "확인 필요",
                PolicyIncomeCondition.UNKNOWN, null, null,
                0.5, "확인 필요",
                "신청 대상 설명", "월 최대 20만 원",
                "지원 혜택 설명", "온라인으로 신청해요.");
        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cardNewsRepository.existsByPolicy_PolicyIdAndCardNo("policy-1", 1L))
                .thenReturn(false);
        Region firstRegion = Region.builder()
                .regionCode("11110").sigunguName("서울특별시 종로구").build();
        when(regionRepository.findAllById(any())).thenReturn(List.of(firstRegion));
        List<CardNews> savedCardNews = new ArrayList<>();
        when(cardNewsRepository.save(any(CardNews.class))).thenAnswer(invocation -> {
            CardNews cardNews = invocation.getArgument(0);
            savedCardNews.add(cardNews);
            return cardNews;
        });

        service.save(item, analysis);

        verify(policyRepository).save(any(Policy.class));
        verify(policyRegionRepository).deleteAllByPolicy_PolicyId("policy-1");
        verify(policyRegionRepository).saveAll(org.mockito.ArgumentMatchers.argThat(regions -> {
            java.util.List<com.hyeja.policyservice.policy.entity.PolicyRegion> values = new java.util.ArrayList<>();
            regions.forEach(values::add);
            return values.size() == 1
                    && values.get(0).getRegion().getRegionCode().equals("11110");
        }));
        assertThat(savedCardNews).extracting(CardNews::getCardNo)
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(savedCardNews.get(0).getPolicy().getPolicyId()).isEqualTo("policy-1");
        assertThat(savedCardNews.get(0).getBody()).isEqualTo("청년의 주거비를 지원합니다.");
        assertThat(savedCardNews.get(1).getTitle()).isNull();
        assertThat(savedCardNews.get(1).getBody()).isEqualTo("신청 대상 설명");
        assertThat(savedCardNews.get(2).getTitle()).isEqualTo("월 최대 20만 원");
        assertThat(savedCardNews.get(3).getBody()).isEqualTo("온라인으로 신청해요.");
        assertThat(savedCardNews.get(0).getPolicy().getDescription())
                .isEqualTo("청년의 주거비를 지원합니다.");
        assertThat(savedCardNews.get(0).getPolicy().getCategories())
                .containsExactly(PolicyCategory.OTHER);
    }

    @Test
    void saveUsesIndependentTransaction() throws NoSuchMethodException {
        Transactional transactional = PolicySyncItemService.class
                .getMethod("save", PolicyItem.class, PolicyAiAnalysis.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    // 시·도 코드(11000)는 REGION에 새 행을 만들지 않고 그 시·도의 시군구 전체로 풀어서 저장합니다.
    // 예전 수집이 만든 시·도 행이 REGION에 이미 있어도 그 행이 아니라 시군구로 저장합니다.
    @Test
    void expandsSidoCodeToSigunguRegionsWithoutCreatingRegion() {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("seoul-policy");
        item.setPolicyName("서울특별시 지원 정책");
        item.setRegionCodes("11000");
        item.setApplyPeriodCode("57002");
        PolicyAiAnalysis analysis = new PolicyAiAnalysis(
                "서울특별시 지원 정책입니다.",
                java.util.Set.of(PolicyCategory.OTHER), 0.8, "기타",
                com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement.UNKNOWN,
                0.5, "확인 필요",
                PolicyIncomeCondition.UNKNOWN, null, null,
                0.5, "확인 필요");
        Region jongno = Region.builder()
                .regionCode("11110").sigunguName("서울특별시 종로구").build();
        Region gangnam = Region.builder()
                .regionCode("11680").sigunguName("서울특별시 강남구").build();
        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Region legacySido = Region.builder().regionCode("11000").sigunguName("서울특별시").build();
        when(regionRepository.findAllById(any())).thenReturn(List.of(legacySido));
        when(regionRepository.findAllByRegionCodeStartingWith("11"))
                .thenReturn(List.of(legacySido, jongno, gangnam));
        when(cardNewsRepository.existsByPolicy_PolicyIdAndCardNo("seoul-policy", 1L))
                .thenReturn(true);

        service.save(item, analysis);

        verify(regionRepository).findAllByRegionCodeStartingWith("11");
        verify(regionRepository, never()).save(any(Region.class));
        verify(policyRegionRepository).saveAll(org.mockito.ArgumentMatchers.argThat(regions -> {
            java.util.List<com.hyeja.policyservice.policy.entity.PolicyRegion> values =
                    new java.util.ArrayList<>();
            regions.forEach(values::add);
            return values.stream().map(value -> value.getRegion().getRegionCode()).toList()
                    .equals(List.of("11110", "11680"));
        }));
    }

    // 지역 코드가 여러 개면 전부 저장합니다. REGION에 없는 코드(99999)는 건너뜁니다.
    @Test
    void savesAllRegionCodesAndSkipsUnknownCode() {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("nationwide-policy");
        item.setPolicyName("전국 정책");
        List<Region> regions = IntStream.rangeClosed(1, 200)
                .mapToObj(number -> Region.builder()
                        .regionCode(String.format("%05d", number))
                        .sigunguName("테스트도 지역" + number)
                        .build())
                .toList();
        item.setRegionCodes(regions.stream()
                .map(Region::getRegionCode)
                .collect(java.util.stream.Collectors.joining(",")) + ",99999");
        when(regionRepository.findAllById(any())).thenReturn(regions);
        item.setApplyPeriodCode("57002");
        PolicyAiAnalysis analysis = new PolicyAiAnalysis(
                "전국 지원 정책입니다.",
                java.util.Set.of(PolicyCategory.OTHER), 0.8, "기타",
                com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement.UNKNOWN,
                0.5, "확인 필요",
                PolicyIncomeCondition.UNKNOWN, null, null,
                0.5, "확인 필요");
        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cardNewsRepository.existsByPolicy_PolicyIdAndCardNo("nationwide-policy", 1L))
                .thenReturn(true);

        service.save(item, analysis);

        verify(policyRegionRepository).saveAll(org.mockito.ArgumentMatchers.argThat(values -> {
            java.util.List<com.hyeja.policyservice.policy.entity.PolicyRegion> policyRegions =
                    new java.util.ArrayList<>();
            values.forEach(policyRegions::add);
            return policyRegions.size() == 200;
        }));
    }
}
