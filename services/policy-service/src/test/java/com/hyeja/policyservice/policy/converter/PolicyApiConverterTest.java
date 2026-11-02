package com.hyeja.policyservice.policy.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.policyservice.policy.enums.PolicyMarriageCondition;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

// 원본 backend main 3e47bb1의 PolicyServiceTest에서 변환 검증 항목을 이관했습니다.
class PolicyApiConverterTest {
    private final PolicyApiCodeConverter codeConverter = new PolicyApiCodeConverter();
    private final PolicyApiConverter apiConverter = new PolicyApiConverter(codeConverter);

    @Test
    void mapsYouthPolicyApiFieldsToPolicyEntity() {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("202609250001");
        item.setPolicyName(" 청년 주거 지원 ");
        item.setCategory("주거");
        item.setSubCategory("주택 및 거주지");
        item.setKeywords("청년,월세");
        item.setPolicyExplanation("API에서 받은 원문 설명");
        item.setSupportContent("월 20만 원 지원");
        item.setMinAge("19"); item.setMaxAge("39"); item.setAgeLimitYn("Y");
        item.setIncomeConditionCode("0043002");
        item.setIncomeMin("100"); item.setIncomeMax("300");
        item.setIncomeEtc("기준중위소득 기준");
        item.setMarriageCode("0055001");
        item.setEmploymentCodes("0013001,0013002");
        item.setApplyPeriodCode("0057001");
        item.setExtraQualification("서울 거주");
        item.setParticipantTarget("무주택 청년");
        item.setApplyYmd("20260901 ~ 20260930");
        item.setApplyMethod("온라인 신청");
        item.setApplyUrl("https://example.com/apply");
        item.setReferenceUrl1("https://example.com/reference");
        item.setViewCount("123");
        item.setApprovalStatusCode("0044002");

        Policy policy = apiConverter.convert(item, PolicyCategory.MONTHLY_RENT,
                PolicyHouselessRequirement.REQUIRED,
                PolicyIncomeCondition.COMPARABLE, 100, 300);

        assertThat(policy.getPolicyId()).isEqualTo("202609250001");
        assertThat(policy.getPolicyName()).isEqualTo("청년 주거 지원");
        assertThat(policy.getApiSubCategory()).isEqualTo("주택 및 거주지");
        assertThat(policy.getCategories()).containsExactly(PolicyCategory.MONTHLY_RENT);
        assertThat(policy.getDescription()).isNull();
        assertThat(policy.getSupportContent()).isEqualTo("월 20만 원 지원");
        assertThat(policy.getMinAge()).isEqualTo(19);
        assertThat(policy.getMaxAge()).isEqualTo(39);
        assertThat(policy.getAgeLimitYn()).isTrue();
        assertThat(policy.getIncomeMin()).isEqualTo(100);
        assertThat(policy.getIncomeMax()).isEqualTo(300);
        assertThat(policy.getMarriageCode()).isEqualTo(PolicyMarriageCondition.MARRIED);
        assertThat(policy.getEmploymentCodes()).containsExactlyInAnyOrder(
                PolicyEmploymentCondition.EMPLOYED,
                PolicyEmploymentCondition.SELF_EMPLOYED);
        assertThat(policy.getHouselessRequirement())
                .isEqualTo(PolicyHouselessRequirement.REQUIRED);
        assertThat(policy.getApplyPeriodCode()).isEqualTo(PolicyApplyPeriod.SPECIFIC_PERIOD);
        assertThat(policy.getHousingType()).isEqualTo("주택 및 거주지");
        assertThat(policy.getExtraQualification()).contains("서울 거주", "무주택 청년");
        assertThat(policy.getApplyStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(policy.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(policy.getViewCount()).isEqualTo(123);
        assertThat(policy.getActiveYn()).isTrue();
    }

    @Test
    void safelyMapsBlankAndNonNumericOptionalValues() {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("policy-2"); item.setPolicyName(" "); item.setCategory("주거");
        item.setMinAge("-"); item.setApplyYmd("2026년 연중");
        item.setApplyPeriodCode("57002");

        Policy policy = apiConverter.convert(item, PolicyCategory.OTHER, null,
                PolicyIncomeCondition.UNKNOWN, null, null);

        assertThat(policy.getPolicyName()).isEqualTo("제목 없음");
        assertThat(policy.getMinAge()).isNull();
        assertThat(policy.getApplyStartDate()).isNull();
        assertThat(policy.getApplyEndDate()).isNull();
        assertThat(policy.getApplyPeriodCode()).isEqualTo(PolicyApplyPeriod.ALWAYS);
        assertThat(policy.getViewCount()).isZero();
    }

    @Test
    void mapsApplyPeriodCodesAndUsesDatesOnlyForSpecificPeriod() {
        PolicyItem specific = new PolicyItem();
        specific.setPolicyId("specific");
        specific.setPolicyName("특정기간 정책");
        specific.setApplyPeriodCode("0057001");
        specific.setApplyYmd("20260901 ~ 20260930");

        PolicyItem always = new PolicyItem();
        always.setPolicyId("always");
        always.setPolicyName("상시 정책");
        always.setApplyPeriodCode("57002");
        always.setApplyYmd("20260901 ~ 20260930");

        PolicyItem closed = new PolicyItem();
        closed.setPolicyId("closed");
        closed.setPolicyName("마감 정책");
        closed.setApplyPeriodCode("57003");
        closed.setApplyYmd("20260901 ~ 20260930");

        Policy specificPolicy = apiConverter.convert(specific, PolicyCategory.OTHER,
                PolicyHouselessRequirement.UNKNOWN,
                PolicyIncomeCondition.UNKNOWN, null, null);
        Policy alwaysPolicy = apiConverter.convert(always, PolicyCategory.OTHER,
                PolicyHouselessRequirement.UNKNOWN,
                PolicyIncomeCondition.UNKNOWN, null, null);
        Policy closedPolicy = apiConverter.convert(closed, PolicyCategory.OTHER,
                PolicyHouselessRequirement.UNKNOWN,
                PolicyIncomeCondition.UNKNOWN, null, null);

        assertThat(specificPolicy.getApplyPeriodCode())
                .isEqualTo(PolicyApplyPeriod.SPECIFIC_PERIOD);
        assertThat(specificPolicy.getApplyStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(specificPolicy.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(alwaysPolicy.getApplyPeriodCode()).isEqualTo(PolicyApplyPeriod.ALWAYS);
        assertThat(alwaysPolicy.getApplyStartDate()).isNull();
        assertThat(alwaysPolicy.getApplyEndDate()).isNull();
        assertThat(closedPolicy.getApplyPeriodCode()).isEqualTo(PolicyApplyPeriod.CLOSED);
        assertThat(closedPolicy.getApplyStartDate()).isNull();
        assertThat(closedPolicy.getApplyEndDate()).isNull();
    }

    @Test
    void mapsZeroAgeRangeToNoAgeLimit() {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("no-age-limit-policy");
        item.setPolicyName("연령 제한 없는 정책");
        item.setMinAge("0");
        item.setMaxAge("0");
        item.setAgeLimitYn("Y");
        item.setApplyPeriodCode("57002");

        Policy policy = apiConverter.convert(item, PolicyCategory.OTHER, null,
                PolicyIncomeCondition.UNKNOWN, null, null);

        assertThat(policy.getMinAge()).isNull();
        assertThat(policy.getMaxAge()).isNull();
        assertThat(policy.getAgeLimitYn()).isFalse();
    }

    @Test
    void mapsNonApprovedPolicyAsInactive() {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("policy-3");
        item.setPolicyName("승인 대기 정책");
        item.setCategory("주거");
        item.setApprovalStatusCode("NOT_APPROVED");
        item.setApplyPeriodCode("57003");

        assertThat(apiConverter.convert(item, PolicyCategory.OTHER, null,
                PolicyIncomeCondition.UNKNOWN, null, null).getActiveYn()).isFalse();
    }

    @Test
    void mapsMarriageConditionCodesToReadableValues() {
        assertThat(mapMarriageCondition("0055001")).isEqualTo(PolicyMarriageCondition.MARRIED);
        assertThat(mapMarriageCondition("55002")).isEqualTo(PolicyMarriageCondition.SINGLE);
        assertThat(mapMarriageCondition("0055003"))
                .isEqualTo(PolicyMarriageCondition.NO_RESTRICTION);
        assertThat(mapMarriageCondition("unknown")).isNull();
        assertThat(mapMarriageCondition(" ")).isNull();
    }

    @Test
    void mapsEmploymentCodesToReadableValues() {
        assertThat(mapEmploymentConditions("0013001"))
                .containsExactly(PolicyEmploymentCondition.EMPLOYED);
        assertThat(mapEmploymentConditions("13002"))
                .containsExactly(PolicyEmploymentCondition.SELF_EMPLOYED);
        assertThat(mapEmploymentConditions("0013003"))
                .containsExactly(PolicyEmploymentCondition.UNEMPLOYED);
        assertThat(mapEmploymentConditions("13004"))
                .containsExactly(PolicyEmploymentCondition.FREELANCER);
        assertThat(mapEmploymentConditions("0013005"))
                .containsExactly(PolicyEmploymentCondition.DAILY_WORKER);
        assertThat(mapEmploymentConditions("13006"))
                .containsExactly(PolicyEmploymentCondition.ENTREPRENEUR);
        assertThat(mapEmploymentConditions("0013007"))
                .containsExactly(PolicyEmploymentCondition.SHORT_TERM_WORKER);
        assertThat(mapEmploymentConditions("13008"))
                .containsExactly(PolicyEmploymentCondition.FARMER);
        assertThat(mapEmploymentConditions("0013009"))
                .containsExactly(PolicyEmploymentCondition.OTHER);
        assertThat(mapEmploymentConditions("13010"))
                .containsExactly(PolicyEmploymentCondition.NO_RESTRICTION);
        assertThat(mapEmploymentConditions("0013001,0013004"))
                .containsExactlyInAnyOrder(
                        PolicyEmploymentCondition.EMPLOYED,
                        PolicyEmploymentCondition.FREELANCER);
        assertThat(mapEmploymentConditions("unknown")).isEmpty();
    }

    @Test
    void storesCategoryProvidedByAiClassifier() {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("ai-category-policy");
        item.setPolicyName("청년 주거 정책");
        item.setApplyPeriodCode("57002");

        Policy policy = apiConverter.convert(item, PolicyCategory.PUBLIC_RENT, null,
                PolicyIncomeCondition.UNKNOWN, null, null);

        assertThat(policy.getCategories()).containsExactly(PolicyCategory.PUBLIC_RENT);
    }

    private PolicyMarriageCondition mapMarriageCondition(String code) {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("marriage-test");
        item.setPolicyName("혼인 조건 테스트");
        item.setCategory("주거");
        item.setMarriageCode(code);
        item.setApplyPeriodCode("57002");
        return apiConverter.convert(item, PolicyCategory.OTHER, null,
                PolicyIncomeCondition.UNKNOWN, null, null).getMarriageCode();
    }

    private Set<PolicyEmploymentCondition> mapEmploymentConditions(String codes) {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("employment-test");
        item.setPolicyName("취업 조건 테스트");
        item.setCategory("주거");
        item.setEmploymentCodes(codes);
        item.setApplyPeriodCode("57002");
        return apiConverter.convert(item, PolicyCategory.OTHER, null,
                PolicyIncomeCondition.UNKNOWN, null, null).getEmploymentCodes();
    }
}

