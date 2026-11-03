package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO.ConditionResultDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.EligibilityConditionType;
import com.hyeja.policyservice.policy.enums.EligibilityStatus;
import com.hyeja.policyservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.enums.EmploymentStatus;
import com.hyeja.policyservice.client.user.enums.IncomeRange;
import com.hyeja.policyservice.region.entity.Region;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyEligibilityEvaluatorTest {

    // 한국 2026-09-28 01:00 = UTC 2026-09-27 16:00. 서버 기본 시간대(UTC)로 계산하면 날짜가 하루 어긋나는 시각입니다.
    private static final Clock KOREA_CLOCK = Clock.fixed(
            Instant.parse("2026-09-27T16:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final PolicyEligibilityEvaluator evaluator = new PolicyEligibilityEvaluator(
            new PolicyIncomeEligibilityEvaluator(), KOREA_CLOCK);

    @Test
    void evaluatesFiveConditionsForMember() {
        Policy policy = mock(Policy.class);
        when(policy.getAgeLimitYn()).thenReturn(true);
        when(policy.getMinAge()).thenReturn(19);
        when(policy.getMaxAge()).thenReturn(34);
        when(policy.getIncomeConditionCode()).thenReturn(PolicyIncomeCondition.COMPARABLE);
        when(policy.getIncomeMax()).thenReturn(50_000_000);
        when(policy.getEmploymentCodes()).thenReturn(Set.of(PolicyEmploymentCondition.EMPLOYED));
        when(policy.getHouselessRequirement())
                .thenReturn(PolicyHouselessRequirement.REQUIRED);

        Region region = mock(Region.class);
        when(region.getSigunguName()).thenReturn("서울특별시 강남구");
        MemberProfileDTO profile = mock(MemberProfileDTO.class);
        when(profile.birth()).thenReturn(LocalDate.now(KOREA_CLOCK).minusYears(27));
        MemberProfileDTO.RegionDTO profileRegion = memberRegion(region);
        when(profile.region()).thenReturn(profileRegion);
        when(profile.incomeRangeCode()).thenReturn(IncomeRange.R3000_4000);
        when(profile.employmentCode()).thenReturn(EmploymentStatus.EMPLOYED);
        when(profile.houselessYn()).thenReturn(true);

        List<ConditionResultDTO> results = evaluator.evaluate(policy, profile, List.of());

        assertThat(results).extracting(ConditionResultDTO::type)
                .containsExactly(
                        EligibilityConditionType.AGE,
                        EligibilityConditionType.REGION,
                        EligibilityConditionType.INCOME,
                        EligibilityConditionType.EMPLOYMENT,
                        EligibilityConditionType.HOUSELESS);
        assertThat(results).extracting(ConditionResultDTO::status)
                .containsOnly(EligibilityStatus.ABLE);
    }

    @Test
    void displaysNoRestrictionForPolicyWithoutAgeLimit() {
        Policy policy = mock(Policy.class);
        when(policy.getAgeLimitYn()).thenReturn(false);
        MemberProfileDTO profile = mock(MemberProfileDTO.class);
        when(profile.birth()).thenReturn(LocalDate.now(KOREA_CLOCK).minusYears(27));

        ConditionResultDTO result = evaluator.evaluate(policy, profile, List.of()).stream()
                .filter(condition -> condition.type() == EligibilityConditionType.AGE)
                .findFirst().orElseThrow();

        assertThat(result.status()).isEqualTo(EligibilityStatus.ABLE);
        assertThat(result.policyCondition()).isEqualTo("제한 없음");
    }

    @Test
    void treatsLegacyZeroAgeRangeAsNoRestriction() {
        Policy policy = mock(Policy.class);
        when(policy.getAgeLimitYn()).thenReturn(true);
        when(policy.getMinAge()).thenReturn(0);
        when(policy.getMaxAge()).thenReturn(0);
        MemberProfileDTO profile = mock(MemberProfileDTO.class);
        when(profile.birth()).thenReturn(LocalDate.now(KOREA_CLOCK).minusYears(27));

        ConditionResultDTO result = evaluator.evaluate(policy, profile, List.of()).stream()
                .filter(condition -> condition.type() == EligibilityConditionType.AGE)
                .findFirst().orElseThrow();

        assertThat(result.status()).isEqualTo(EligibilityStatus.ABLE);
        assertThat(result.policyCondition()).isEqualTo("제한 없음");
    }

    @Test
    void displaysRegionNameFromPolicyRegion() {
        Policy policy = mock(Policy.class);
        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("11680");
        when(region.getSigunguName()).thenReturn("서울특별시 강남구");
        com.hyeja.policyservice.policy.entity.PolicyRegion policyRegion =
                mock(com.hyeja.policyservice.policy.entity.PolicyRegion.class);
        when(policyRegion.getRegion()).thenReturn(region);
        MemberProfileDTO profile = mock(MemberProfileDTO.class);
        MemberProfileDTO.RegionDTO profileRegion = memberRegion(region);
        when(profile.region()).thenReturn(profileRegion);

        ConditionResultDTO result = evaluator.evaluate(
                        policy, profile, List.of(policyRegion)).stream()
                .filter(condition -> condition.type() == EligibilityConditionType.REGION)
                .findFirst().orElseThrow();

        assertThat(result.status()).isEqualTo(EligibilityStatus.ABLE);
        assertThat(result.policyCondition()).isEqualTo("서울특별시 강남구");
    }

    @Test
    void returnsUnknownWhenPolicyOrMemberInformationIsInsufficient() {
        Policy policy = mock(Policy.class);
        when(policy.getAgeLimitYn()).thenReturn(true);
        when(policy.getMinAge()).thenReturn(null);
        when(policy.getMaxAge()).thenReturn(null);
        when(policy.getIncomeConditionCode()).thenReturn(PolicyIncomeCondition.CONDITIONAL);
        when(policy.getHouselessRequirement())
                .thenReturn(PolicyHouselessRequirement.UNKNOWN);
        MemberProfileDTO profile = mock(MemberProfileDTO.class);

        List<ConditionResultDTO> results = evaluator.evaluate(policy, profile, List.of());

        assertThat(results).filteredOn(result ->
                        result.type() != EligibilityConditionType.REGION)
                .extracting(ConditionResultDTO::status)
                .containsOnly(EligibilityStatus.UNKNOWN);
        assertThat(results).filteredOn(result ->
                        result.type() == EligibilityConditionType.REGION)
                .extracting(ConditionResultDTO::status)
                .containsExactly(EligibilityStatus.ABLE);
    }

    @Test
    void comparesPolicyAndMemberSigunguRegionCodes() {
        Policy policy = mock(Policy.class);
        when(policy.getAgeLimitYn()).thenReturn(false);
        when(policy.getIncomeConditionCode()).thenReturn(PolicyIncomeCondition.NO_RESTRICTION);
        when(policy.getEmploymentCodes()).thenReturn(
                Set.of(PolicyEmploymentCondition.NO_RESTRICTION));
        when(policy.getHouselessRequirement())
                .thenReturn(PolicyHouselessRequirement.NOT_REQUIRED);
        Region policyRegion = mock(Region.class);
        when(policyRegion.getRegionCode()).thenReturn("11440");
        when(policyRegion.getSigunguName()).thenReturn("서울특별시 마포구");
        com.hyeja.policyservice.policy.entity.PolicyRegion policyRegionLink =
                mock(com.hyeja.policyservice.policy.entity.PolicyRegion.class);
        when(policyRegionLink.getRegion()).thenReturn(policyRegion);

        Region matchingRegion = mock(Region.class);
        when(matchingRegion.getRegionCode()).thenReturn("11440");
        when(matchingRegion.getSigunguName()).thenReturn("서울특별시 마포구");
        MemberProfileDTO matchingProfile = mock(MemberProfileDTO.class);
        MemberProfileDTO.RegionDTO matchingProfileRegion = memberRegion(matchingRegion);
        when(matchingProfile.region()).thenReturn(matchingProfileRegion);

        Region differentRegion = mock(Region.class);
        when(differentRegion.getRegionCode()).thenReturn("11680");
        when(differentRegion.getSigunguName()).thenReturn("서울특별시 강남구");
        MemberProfileDTO differentProfile = mock(MemberProfileDTO.class);
        MemberProfileDTO.RegionDTO differentProfileRegion = memberRegion(differentRegion);
        when(differentProfile.region()).thenReturn(differentProfileRegion);

        ConditionResultDTO matchingResult = evaluator.evaluate(
                        policy, matchingProfile, List.of(policyRegionLink)).stream()
                .filter(result -> result.type() == EligibilityConditionType.REGION)
                .findFirst().orElseThrow();
        ConditionResultDTO differentResult = evaluator.evaluate(
                        policy, differentProfile, List.of(policyRegionLink)).stream()
                .filter(result -> result.type() == EligibilityConditionType.REGION)
                .findFirst().orElseThrow();

        assertThat(matchingResult.status()).isEqualTo(EligibilityStatus.ABLE);
        assertThat(matchingResult.policyCondition()).isEqualTo("서울특별시 마포구");
        assertThat(matchingResult.memberValue()).isEqualTo("서울특별시 마포구");
        assertThat(differentResult.status()).isEqualTo(EligibilityStatus.DISABLE);
        assertThat(differentResult.memberValue()).isEqualTo("서울특별시 강남구");
    }

    // 한국 날짜로 생일 당일(2000-09-28)이면 만 26세입니다. UTC 날짜(09-27)로 세면 25세가 됩니다.
    @Test
    void countsAgeByKoreanDate() {
        Policy policy = mock(Policy.class);
        when(policy.getAgeLimitYn()).thenReturn(true);
        when(policy.getMinAge()).thenReturn(26);
        when(policy.getMaxAge()).thenReturn(34);
        MemberProfileDTO profile = mock(MemberProfileDTO.class);
        when(profile.birth()).thenReturn(LocalDate.of(2000, 9, 28));

        ConditionResultDTO age = evaluator.evaluate(policy, profile, List.of()).stream()
                .filter(condition -> condition.type() == EligibilityConditionType.AGE)
                .findFirst().orElseThrow();

        assertThat(age.status()).isEqualTo(EligibilityStatus.ABLE);
        assertThat(age.memberValue()).isEqualTo("만 26세");
    }

    // 여러 지역 정책은 그중 하나라도 회원 거주지와 같으면 가능하고, 조건은 "첫 지역 외 N곳"으로 보여 줍니다.
    @Test
    void matchesAnyOfMultiplePolicyRegions() {
        Policy policy = mock(Policy.class);
        List<com.hyeja.policyservice.policy.entity.PolicyRegion> policyRegions = List.of(
                policyRegion("11110", "서울특별시 종로구"),
                policyRegion("11440", "서울특별시 마포구"),
                policyRegion("11680", "서울특별시 강남구"));

        ConditionResultDTO mapo = regionResult(policy, "11440", "서울특별시 마포구", policyRegions);
        ConditionResultDTO busan = regionResult(policy, "26110", "부산광역시 중구", policyRegions);

        assertThat(mapo.status()).isEqualTo(EligibilityStatus.ABLE);
        assertThat(mapo.policyCondition()).isEqualTo("서울특별시 종로구 외 2곳");
        assertThat(busan.status()).isEqualTo(EligibilityStatus.DISABLE);
    }

    private com.hyeja.policyservice.policy.entity.PolicyRegion policyRegion(String code, String name) {
        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn(code);
        when(region.getSigunguName()).thenReturn(name);
        com.hyeja.policyservice.policy.entity.PolicyRegion policyRegion =
                mock(com.hyeja.policyservice.policy.entity.PolicyRegion.class);
        when(policyRegion.getRegion()).thenReturn(region);
        return policyRegion;
    }

    private ConditionResultDTO regionResult(Policy policy, String memberCode, String memberName,
            List<com.hyeja.policyservice.policy.entity.PolicyRegion> policyRegions) {
        Region memberRegion = mock(Region.class);
        when(memberRegion.getRegionCode()).thenReturn(memberCode);
        when(memberRegion.getSigunguName()).thenReturn(memberName);
        MemberProfileDTO profile = mock(MemberProfileDTO.class);
        MemberProfileDTO.RegionDTO profileRegion = memberRegion(memberRegion);
        when(profile.region()).thenReturn(profileRegion);
        return evaluator.evaluate(policy, profile, policyRegions).stream()
                .filter(condition -> condition.type() == EligibilityConditionType.REGION)
                .findFirst().orElseThrow();
    }

    // 1차 테스트는 회원 지역에 Region 엔티티를 썼고, policy는 F6 응답의 지역(regionCode, sigunguName)을 받습니다.
    private static MemberProfileDTO.RegionDTO memberRegion(
            com.hyeja.policyservice.region.entity.Region region) {
        return new MemberProfileDTO.RegionDTO(region.getRegionCode(), region.getSigunguName());
    }
}
