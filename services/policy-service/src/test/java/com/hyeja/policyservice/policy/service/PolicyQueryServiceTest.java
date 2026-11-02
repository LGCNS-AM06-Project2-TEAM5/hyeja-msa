package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.hyeja.policyservice.policy.converter.PolicyApiCodeConverter;
import com.hyeja.policyservice.policy.dto.PolicyDetailResponseDTO;
import com.hyeja.policyservice.policy.dto.PolicyGuestResponseDTO;
import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.*;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.term.entity.Term;
import com.hyeja.policyservice.term.repository.TermRepository;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.client.RestTemplate;

class PolicyQueryServiceTest {
    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyRegionRepository policyRegionRepository = mock(PolicyRegionRepository.class);
    private final TermRepository termRepository = mock(TermRepository.class);
    private final PolicyMemberService policyMemberService = mock(PolicyMemberService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-27T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final PolicyService service = new PolicyService(mock(RestTemplate.class),
            new PolicyApiCodeConverter(), mock(PolicyAiAnalyzer.class), mock(PolicySyncItemService.class),
            policyRepository, policyRegionRepository, termRepository, clock, policyMemberService);

    @Test
    void returnsGuestPolicyPageWithRegionsAndDeadlineInformation() {
        LocalDate today = LocalDate.now(clock);
        Region region = Region.builder()
                .regionCode("11440")
                .sigunguName("서울특별시 마포구")
                .build();
        Policy regionalPolicy = Policy.builder()
                .policyId("POLICY-1")
                .policyName("청년 월세 지원")
                .categories(Set.of(PolicyCategory.MONTHLY_RENT))
                .ageLimitYn(false)
                .applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD)
                .applyEndDate(today.plusDays(4))
                .build();
        Policy alwaysOpenPolicy = Policy.builder()
                .policyId("POLICY-2")
                .policyName("상시 주거 상담")
                .categories(Set.of(PolicyCategory.OTHER))
                .ageLimitYn(false)
                .applyPeriodCode(PolicyApplyPeriod.ALWAYS)
                .applyEndDate(null)
                .build();
        PageRequest pageRequest = PageRequest.of(0, 8, PolicySort.DEADLINE.toSort());
        when(policyRepository.findGuestHousingPolicies(
                PolicyCategory.MONTHLY_RENT.name(), today, pageRequest))
                .thenReturn(new PageImpl<>(
                        List.of(regionalPolicy, alwaysOpenPolicy), pageRequest, 9));
        when(policyRegionRepository.findAllActiveByPolicyIds(
                List.of("POLICY-1", "POLICY-2")))
                .thenReturn(List.of(PolicyRegion.builder()
                        .policy(regionalPolicy)
                        .region(region)
                        .build()));

        PolicyGuestResponseDTO.PolicyListDTO result = service.getGuestHousingPolicies(
                PolicyCategory.MONTHLY_RENT, PolicySort.DEADLINE, 0, 8);

        assertThat(result.getPolicies()).hasSize(2);
        assertThat(result.getPolicies().get(0)).satisfies(item -> {
            assertThat(item.getPolicyId()).isEqualTo("POLICY-1");
            assertThat(item.getCategoryCodes()).containsExactly(PolicyCategory.MONTHLY_RENT);
            assertThat(item.getCategoryNames()).containsExactly("월세");
            assertThat(item.getRegions()).singleElement().satisfies(itemRegion -> {
                assertThat(itemRegion.getRegionCode()).isEqualTo("11440");
                assertThat(itemRegion.getRegionName()).isEqualTo("서울특별시 마포구");
            });
            assertThat(item.isNationwide()).isFalse();
            assertThat(item.getRegionSummary()).isEqualTo("서울특별시 마포구");
            assertThat(item.getApplyPeriodCode()).isEqualTo(PolicyApplyPeriod.SPECIFIC_PERIOD);
            assertThat(item.getDDay()).isEqualTo(4);
        });
        assertThat(result.getPolicies().get(1)).satisfies(item -> {
            assertThat(item.getPolicyId()).isEqualTo("POLICY-2");
            assertThat(item.getRegions()).isEmpty();
            assertThat(item.isNationwide()).isTrue();
            assertThat(item.getRegionSummary()).isEqualTo("전국");
            assertThat(item.getApplyEndDate()).isNull();
            assertThat(item.getApplyPeriodCode()).isEqualTo(PolicyApplyPeriod.ALWAYS);
            assertThat(item.getDDay()).isNull();
        });
        assertThat(result.getTotalElements()).isEqualTo(9);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.isHasNext()).isTrue();
    }

    @Test
    void returnsPolicyDetailWithoutConditionsForGuest() {
        Policy policy = mock(Policy.class);
        when(policy.getPolicyId()).thenReturn("policy-detail");
        when(policy.getPolicyName()).thenReturn("청년 주거 정책");
        when(policy.getCategories()).thenReturn(Set.of(PolicyCategory.OTHER));
        when(policyRepository.findById("policy-detail")).thenReturn(Optional.of(policy));

        PolicyDetailResponseDTO response = service.getPolicyDetailForMember("policy-detail", null);

        assertThat(response.policyName()).isEqualTo("청년 주거 정책");
        assertThat(response.conditions()).isEmpty();
        assertThat(response.overallStatus()).isNull();
        assertThat(response.isFavorite()).isFalse();
        verifyNoInteractions(policyMemberService);
    }

    @Test
    void emptyPageDoesNotIssueRegionBatchQuery() {
        when(policyRepository.findGuestHousingPolicies(any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 8)));
        assertThat(service.getGuestHousingPolicies(null, PolicySort.DEADLINE, 0, 8).getPolicies()).isEmpty();
        verifyNoInteractions(policyRegionRepository);
    }

    @Test
    void missingPolicyReturnsOriginalErrorBeforeMemberLookup() {
        when(policyRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPolicyDetailForMember("missing", 1L))
                .isInstanceOf(GeneralException.class).extracting("code").isEqualTo(ErrorStatus.POLICY_NOT_FOUND);
        verifyNoInteractions(policyMemberService, termRepository);
    }

    @Test
    void includesOnlyTermsFoundInExtraQualification() {
        Policy policy = policy();
        when(policy.getExtraQualification()).thenReturn("무주택자이며 중위소득 기준을 충족해야 합니다.");
        Term matching = mock(Term.class);
        when(matching.getTermId()).thenReturn(7L);
        when(matching.getTerm()).thenReturn("중위소득");
        Term other = mock(Term.class);
        when(other.getTerm()).thenReturn("보증금");
        Term blank = mock(Term.class);
        when(blank.getTerm()).thenReturn(" ");
        when(termRepository.findAllByDeletedAtIsNullOrderByTermIdAsc())
                .thenReturn(List.of(matching, other, blank));
        var result = service.getPolicyDetailForMember("detail", null);
        assertThat(result.terms()).containsExactly(new PolicyDetailResponseDTO.TermSummaryDTO(7L, "중위소득"));
        verifyNoInteractions(policyMemberService);
    }

    @Test
    void mapsMemberHookResultWithoutDoingMemberBusinessLogic() {
        Policy policy = policy();
        var condition = new PolicyMemberDetailDTO.ConditionResultDTO(
                EligibilityConditionType.AGE, EligibilityStatus.ABLE, "만 19세 이상", "27세");
        when(policyMemberService.getMemberDetail(policy, 42L))
                .thenReturn(new PolicyMemberDetailDTO(true, EligibilityStatus.ABLE, List.of(condition)));
        var result = service.getPolicyDetailForMember("detail", 42L);
        assertThat(result.isFavorite()).isTrue();
        assertThat(result.overallStatus()).isEqualTo(EligibilityStatus.ABLE);
        assertThat(result.conditions()).containsExactly(new PolicyDetailResponseDTO.ConditionResultDTO(
                condition.type(), condition.status(), condition.policyCondition(), condition.memberValue()));
        verify(policyMemberService).getMemberDetail(policy, 42L);
    }

    private Policy policy() {
        Policy policy = mock(Policy.class);
        when(policy.getCategories()).thenReturn(Set.of(PolicyCategory.OTHER));
        when(policyRepository.findById("detail")).thenReturn(Optional.of(policy));
        return policy;
    }
}
