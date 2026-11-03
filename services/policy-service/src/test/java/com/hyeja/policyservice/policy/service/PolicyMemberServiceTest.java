package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hyeja.policyservice.client.engagement.FavoriteReader;
import com.hyeja.policyservice.client.user.MemberProfileReader;
import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO;
import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO.ConditionResultDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.EligibilityConditionType;
import com.hyeja.policyservice.policy.enums.EligibilityStatus;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.client.user.enums.EmploymentStatus;
import com.hyeja.policyservice.policy.dto.PolicyMemberResponseDTO.PolicyListDTO;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicySort;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.region.entity.Region;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyMemberServiceTest {

    private final MemberProfileReader memberProfileReader = mock(MemberProfileReader.class);
    private final PolicyRegionRepository policyRegionRepository = mock(PolicyRegionRepository.class);
    private final PolicyEligibilityEvaluator evaluator = mock(PolicyEligibilityEvaluator.class);
    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final FavoriteReader favoriteReader = mock(FavoriteReader.class);
    // 한국 2026-09-27
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-26T16:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final PolicyMemberService service = new PolicyMemberService(
            memberProfileReader, favoriteReader, policyRepository, policyRegionRepository, evaluator, clock);

    private final Policy policy = policy("P1");
    private final MemberProfileDTO profile = mock(MemberProfileDTO.class);

    @Test
    void 회원_조건으로_판정하고_하나라도_불가면_전체_불가() {
        List<PolicyRegion> regions = List.of(mock(PolicyRegion.class));
        given(memberProfileReader.getActiveProfile(7L)).willReturn(profile);
        given(policyRegionRepository.findAllByPolicy_PolicyId("P1")).willReturn(regions);
        List<ConditionResultDTO> conditions = conditions(
                EligibilityStatus.ABLE, EligibilityStatus.UNKNOWN, EligibilityStatus.DISABLE);
        given(evaluator.evaluate(policy, profile, regions)).willReturn(conditions);

        PolicyMemberDetailDTO detail = service.getMemberDetail(policy, 7L);

        assertThat(detail.conditions()).isEqualTo(conditions);
        assertThat(detail.overallStatus()).isEqualTo(EligibilityStatus.DISABLE);
        assertThat(detail.isFavorite()).isFalse();
    }

    @Test
    void 불가가_없고_확인_필요가_있으면_전체_확인_필요() {
        givenConditions(EligibilityStatus.ABLE, EligibilityStatus.UNKNOWN);

        assertThat(service.getMemberDetail(policy, 7L).overallStatus())
                .isEqualTo(EligibilityStatus.UNKNOWN);
    }

    @Test
    void 모두_가능하면_전체_가능() {
        givenConditions(EligibilityStatus.ABLE, EligibilityStatus.ABLE);

        assertThat(service.getMemberDetail(policy, 7L).overallStatus())
                .isEqualTo(EligibilityStatus.ABLE);
    }

    @Test
    void user_서비스가_응답하지_않으면_판정_없이_하트만_채운다() {
        given(memberProfileReader.getActiveProfile(7L))
                .willThrow(new GeneralException(ErrorStatus.USER_SERVICE_UNAVAILABLE));
        given(favoriteReader.isFavorite(7L, "P1")).willReturn(true);

        PolicyMemberDetailDTO detail = service.getMemberDetail(policy, 7L);

        assertThat(detail).isEqualTo(new PolicyMemberDetailDTO(true, null, List.of()));
        verifyNoInteractions(evaluator);
    }

    @Test
    void 조건이_없으면_1차처럼_PROFILE_001을_그대로_던진다() {
        given(memberProfileReader.getActiveProfile(7L))
                .willThrow(new GeneralException(ErrorStatus.PROFILE_NOT_FOUND));

        assertThatThrownBy(() -> service.getMemberDetail(policy, 7L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.PROFILE_NOT_FOUND);
    }

    @Test
    void 회원_ID가_없으면_user를_부르지_않는다() {
        assertThat(service.getMemberDetail(policy, null)).isEqualTo(PolicyMemberDetailDTO.guest());
        verifyNoInteractions(memberProfileReader, evaluator, favoriteReader);
    }

    // 1차 PolicyServiceTest.returnsMemberPolicyPageWithRegionsAndFavoriteStatus를 옮긴 것. 하트는 F4로 현재 페이지 정책만 묻습니다.
    @Test
    void 회원_조건으로_정책_목록을_거른다() {
        LocalDate today = LocalDate.of(2026, 9, 27);
        Region region = Region.builder().regionCode("11440").sigunguName("서울특별시 마포구").build();
        given(memberProfileReader.getActiveProfile(1L)).willReturn(new MemberProfileDTO(
                1L, today.minusYears(26), new MemberProfileDTO.RegionDTO("11440", "서울특별시 마포구"),
                EmploymentStatus.EMPLOYED, true, null));
        Policy policy = Policy.builder().policyId("POLICY-1").policyName("청년 월세 지원")
                .categories(Set.of(PolicyCategory.MONTHLY_RENT)).ageLimitYn(true).minAge(19).maxAge(39)
                .applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD).applyEndDate(today.plusDays(4)).build();
        PageRequest pageRequest = PageRequest.of(0, 8, PolicySort.DEADLINE.toSort());
        given(policyRepository.findHousingPoliciesForMember(
                "MONTHLY_RENT", true, today, 26, true, "EMPLOYED", "11440", pageRequest))
                .willReturn(new PageImpl<>(List.of(policy), pageRequest, 9));
        given(policyRegionRepository.findAllActiveByPolicyIds(List.of("POLICY-1")))
                .willReturn(List.of(PolicyRegion.builder().policy(policy).region(region).build()));
        given(favoriteReader.findFavoritePolicyIds(1L, List.of("POLICY-1"))).willReturn(Set.of("POLICY-1"));

        PolicyListDTO result = service.getHousingPoliciesForMember(
                1L, PolicyCategory.MONTHLY_RENT, PolicySort.DEADLINE, true, 0, 8);

        assertThat(result.getPolicies()).singleElement().satisfies(item -> {
            assertThat(item.getPolicyId()).isEqualTo("POLICY-1");
            assertThat(item.getCategoryNames()).containsExactly("월세");
            assertThat(item.getRegions()).singleElement()
                    .satisfies(itemRegion -> assertThat(itemRegion.getRegionCode()).isEqualTo("11440"));
            assertThat(item.isNationwide()).isFalse();
            assertThat(item.getDDay()).isEqualTo(4);
            assertThat(item.isFavoriteYn()).isTrue();
        });
        assertThat(result.getTotalElements()).isEqualTo(9);
        assertThat(result.isHasNext()).isTrue();
    }

    @Test
    void 목록도_조건이_없으면_PROFILE_001() {
        given(memberProfileReader.getActiveProfile(1L))
                .willThrow(new GeneralException(ErrorStatus.PROFILE_NOT_FOUND));

        assertThatThrownBy(() -> service.getHousingPoliciesForMember(1L, null, PolicySort.DEADLINE, false, 0, 8))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.PROFILE_NOT_FOUND);
    }

    // F6 계약대로 user가 응답하지 않으면 비로그인 목록(같은 카테고리·정렬·페이지)으로 대신합니다.
    @Test
    void user_서비스가_실패하면_비로그인_목록으로_대신한다() {
        given(memberProfileReader.getActiveProfile(1L))
                .willThrow(new GeneralException(ErrorStatus.USER_SERVICE_UNAVAILABLE));
        Policy policy = Policy.builder().policyId("GUEST-1").policyName("전국 정책")
                .categories(Set.of(PolicyCategory.JEONSE)).ageLimitYn(false)
                .applyPeriodCode(PolicyApplyPeriod.ALWAYS).build();
        PageRequest pageRequest = PageRequest.of(1, 4, PolicySort.NAME.toSort());
        given(policyRepository.findGuestHousingPolicies("JEONSE", LocalDate.of(2026, 9, 27), pageRequest))
                .willReturn(new PageImpl<>(List.of(policy), pageRequest, 5));
        given(policyRegionRepository.findAllActiveByPolicyIds(List.of("GUEST-1"))).willReturn(List.of());

        PolicyListDTO result = service.getHousingPoliciesForMember(
                1L, PolicyCategory.JEONSE, PolicySort.NAME, true, 1, 4);

        assertThat(result.getPolicies()).extracting(item -> item.getPolicyId()).containsExactly("GUEST-1");
        assertThat(result.getPolicies().get(0).isNationwide()).isTrue();
        assertThat(result.getPage()).isEqualTo(1);
    }

    private void givenConditions(EligibilityStatus... statuses) {
        given(memberProfileReader.getActiveProfile(7L)).willReturn(profile);
        given(policyRegionRepository.findAllByPolicy_PolicyId("P1")).willReturn(List.of());
        given(evaluator.evaluate(any(), any(), any())).willReturn(conditions(statuses));
    }

    private static List<ConditionResultDTO> conditions(EligibilityStatus... statuses) {
        return java.util.Arrays.stream(statuses)
                .map(status -> new ConditionResultDTO(EligibilityConditionType.AGE, status, "조건", "값"))
                .toList();
    }

    private static Policy policy(String policyId) {
        Policy policy = mock(Policy.class);
        given(policy.getPolicyId()).willReturn(policyId);
        return policy;
    }
}
