package com.hyeja.recommendationservice.policy.service;

import com.hyeja.recommendationservice.policy.dto.ConditionResultDTO;
import com.hyeja.recommendationservice.policy.dto.EligibilityPolicyDTO;
import com.hyeja.recommendationservice.policy.enums.EligibilityConditionType;
import com.hyeja.recommendationservice.policy.enums.EligibilityStatus;
import com.hyeja.recommendationservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.recommendationservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.recommendationservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.recommendationservice.profile.dto.EligibilityProfileDTO;
import com.hyeja.recommendationservice.profile.enums.EmploymentStatus;
import com.hyeja.recommendationservice.region.converter.RegionConverter;
import com.hyeja.recommendationservice.region.dto.RegionDTO;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// policy-service의 정책 상세 판정과 같은 규칙입니다(B안). 규칙을 바꾸면 양쪽을 함께 바꿉니다.
@Component
@RequiredArgsConstructor
public class PolicyEligibilityEvaluator {
    private final PolicyIncomeEligibilityEvaluator incomeEvaluator;
    // 한국 시간(Asia/Seoul) 기준 오늘 날짜로 나이를 셉니다. ClockConfig의 koreaClock 빈입니다.
    private final Clock clock;

    public List<ConditionResultDTO> evaluate(
            EligibilityPolicyDTO policy, EligibilityProfileDTO profile, List<RegionDTO> policyRegions) {
        return List.of(
                evaluateAge(policy, profile),
                evaluateRegion(profile, policyRegions),
                evaluateIncome(policy, profile),
                evaluateEmployment(policy, profile),
                evaluateHouseless(policy, profile));
    }

    private ConditionResultDTO evaluateAge(EligibilityPolicyDTO policy, EligibilityProfileDTO profile) {
        String condition = ageCondition(policy);
        if (hasNoAgeLimit(policy)) {
            return result(EligibilityConditionType.AGE, EligibilityStatus.ABLE,
                    "제한 없음", memberAge(profile));
        }
        if (profile.getBirth() == null) {
            return result(EligibilityConditionType.AGE, EligibilityStatus.UNKNOWN,
                    condition, "미입력");
        }

        int age = Period.between(profile.getBirth(), LocalDate.now(clock)).getYears();
        boolean belowMinimum = policy.getMinAge() != null && age < policy.getMinAge();
        boolean aboveMaximum = policy.getMaxAge() != null && age > policy.getMaxAge();
        EligibilityStatus status = belowMinimum || aboveMaximum
                ? EligibilityStatus.DISABLE : EligibilityStatus.ABLE;
        if (policy.getMinAge() == null && policy.getMaxAge() == null) {
            status = EligibilityStatus.UNKNOWN;
        }
        return result(EligibilityConditionType.AGE, status, condition, "만 " + age + "세");
    }

    private ConditionResultDTO evaluateRegion(EligibilityProfileDTO profile, List<RegionDTO> policyRegions) {
        if (policyRegions == null || policyRegions.isEmpty()) {
            return result(EligibilityConditionType.REGION, EligibilityStatus.ABLE,
                    "전국", profileRegion(profile));
        }
        String condition = regionCondition(policyRegions);
        if (profile.getRegion() == null) {
            return result(EligibilityConditionType.REGION, EligibilityStatus.UNKNOWN,
                    condition, "미입력");
        }
        // 정책 지역 중 하나라도 회원 거주지(시군구)와 같으면 가능합니다.
        String memberRegionCode = profile.getRegion().getRegionCode();
        boolean matches = policyRegions.stream()
                .anyMatch(policyRegion -> policyRegion.getRegionCode().equals(memberRegionCode));
        return result(EligibilityConditionType.REGION,
                matches ? EligibilityStatus.ABLE : EligibilityStatus.DISABLE,
                condition, profileRegion(profile));
    }

    // 지역이 여러 곳이면 "서울특별시 종로구 외 24곳"처럼 첫 지역과 나머지 수로 보여 줍니다.
    private String regionCondition(List<RegionDTO> policyRegions) {
        return RegionConverter.summarize(policyRegions);
    }

    private ConditionResultDTO evaluateIncome(EligibilityPolicyDTO policy, EligibilityProfileDTO profile) {
        return result(EligibilityConditionType.INCOME,
                incomeEvaluator.evaluate(policy, profile),
                incomeCondition(policy),
                profile.getIncomeRangeCode() == null
                        ? "미입력" : profile.getIncomeRangeCode().getLabel());
    }

    private ConditionResultDTO evaluateEmployment(EligibilityPolicyDTO policy, EligibilityProfileDTO profile) {
        Set<PolicyEmploymentCondition> conditions = policy.getEmploymentCodes();
        if (conditions == null || conditions.isEmpty()) {
            return result(EligibilityConditionType.EMPLOYMENT, EligibilityStatus.UNKNOWN,
                    "정보 없음", employmentValue(profile));
        }
        if (conditions.contains(PolicyEmploymentCondition.NO_RESTRICTION)) {
            return result(EligibilityConditionType.EMPLOYMENT, EligibilityStatus.ABLE,
                    "취업 상태 제한 없음", employmentValue(profile));
        }
        EmploymentStatus memberEmployment = profile.getEmploymentCode();
        if (memberEmployment == null) {
            return result(EligibilityConditionType.EMPLOYMENT, EligibilityStatus.UNKNOWN,
                    employmentCondition(conditions), "미입력");
        }
        boolean matches = conditions.stream()
                .anyMatch(condition -> condition.name().equals(memberEmployment.name()));
        return result(EligibilityConditionType.EMPLOYMENT,
                matches ? EligibilityStatus.ABLE : EligibilityStatus.DISABLE,
                employmentCondition(conditions), memberEmployment.getLabel());
    }

    private ConditionResultDTO evaluateHouseless(EligibilityPolicyDTO policy, EligibilityProfileDTO profile) {
        PolicyHouselessRequirement requirement = policy.getHouselessRequirement();
        if (requirement == null || requirement == PolicyHouselessRequirement.UNKNOWN) {
            return result(EligibilityConditionType.HOUSELESS, EligibilityStatus.UNKNOWN,
                    "확인 필요", houselessValue(profile));
        }
        if (requirement == PolicyHouselessRequirement.NOT_REQUIRED) {
            return result(EligibilityConditionType.HOUSELESS, EligibilityStatus.ABLE,
                    "무주택 제한 없음", houselessValue(profile));
        }
        if (profile.getHouselessYn() == null) {
            return result(EligibilityConditionType.HOUSELESS, EligibilityStatus.UNKNOWN,
                    "무주택자", "미입력");
        }
        return result(EligibilityConditionType.HOUSELESS,
                profile.getHouselessYn() ? EligibilityStatus.ABLE : EligibilityStatus.DISABLE,
                "무주택자", houselessValue(profile));
    }

    private String ageCondition(EligibilityPolicyDTO policy) {
        if (hasNoAgeLimit(policy)) return "제한 없음";
        if (policy.getMinAge() != null && policy.getMaxAge() != null) {
            return "만 " + policy.getMinAge() + "~" + policy.getMaxAge() + "세";
        }
        if (policy.getMinAge() != null) return "만 " + policy.getMinAge() + "세 이상";
        if (policy.getMaxAge() != null) return "만 " + policy.getMaxAge() + "세 이하";
        return "확인 필요";
    }

    private boolean hasNoAgeLimit(EligibilityPolicyDTO policy) {
        if (Boolean.FALSE.equals(policy.getAgeLimitYn())) return true;
        return policy.getMinAge() != null && policy.getMinAge() <= 0
                && policy.getMaxAge() != null && policy.getMaxAge() <= 0;
    }

    private String memberAge(EligibilityProfileDTO profile) {
        return profile.getBirth() == null ? "미입력"
                : "만 " + Period.between(profile.getBirth(), LocalDate.now(clock)).getYears() + "세";
    }

    private String profileRegion(EligibilityProfileDTO profile) {
        return profile.getRegion() == null ? "미입력" : profile.getRegion().getSigunguName();
    }

    private String incomeCondition(EligibilityPolicyDTO policy) {
        PolicyIncomeCondition condition = policy.getIncomeConditionCode();
        if (condition == null || condition == PolicyIncomeCondition.UNKNOWN) return "확인 필요";
        if (condition == PolicyIncomeCondition.NO_RESTRICTION) return "소득 제한 없음";
        if (condition == PolicyIncomeCondition.CONDITIONAL) {
            return policy.getIncomeEtc() == null ? "복합 소득 조건" : policy.getIncomeEtc();
        }
        String min = policy.getIncomeMin() == null ? null : money(policy.getIncomeMin()) + " 이상";
        String max = policy.getIncomeMax() == null ? null : money(policy.getIncomeMax()) + " 이하";
        if (min != null && max != null) return min + " ~ " + max;
        return min != null ? min : max != null ? max : "확인 필요";
    }

    private String employmentCondition(Set<PolicyEmploymentCondition> conditions) {
        return conditions.stream()
                .map(PolicyEmploymentCondition::getLabel)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private String employmentValue(EligibilityProfileDTO profile) {
        return profile.getEmploymentCode() == null
                ? "미입력" : profile.getEmploymentCode().getLabel();
    }

    private String houselessValue(EligibilityProfileDTO profile) {
        if (profile.getHouselessYn() == null) return "미입력";
        return profile.getHouselessYn() ? "무주택" : "주택 소유";
    }

    private String money(Integer value) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(value) + "원";
    }

    private ConditionResultDTO result(EligibilityConditionType type, EligibilityStatus status,
            String policyCondition, String memberValue) {
        return new ConditionResultDTO(type, status, policyCondition, memberValue);
    }
}
