package com.hyeja.policyservice.policy.service;

import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO.ConditionResultDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.region.converter.RegionConverter;
import com.hyeja.policyservice.policy.enums.EligibilityConditionType;
import com.hyeja.policyservice.policy.enums.EligibilityStatus;
import com.hyeja.policyservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.enums.EmploymentStatus;
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

// 1차(hyeja-backend 5c7ff86) 판정 규칙 그대로입니다. recommendation에도 같은 규칙이 있고 기준은 recommendation이 가집니다(#1 F8).
// 규칙을 바꾸면 양쪽을 함께 바꿉니다.
@Component
@RequiredArgsConstructor
public class PolicyEligibilityEvaluator {
    private final PolicyIncomeEligibilityEvaluator incomeEvaluator;
    // 한국 시간(Asia/Seoul) 기준 오늘 날짜로 나이를 셉니다. ClockConfig의 koreaClock 빈입니다.
    private final Clock clock;

    public List<ConditionResultDTO> evaluate(
            Policy policy, MemberProfileDTO profile, List<PolicyRegion> policyRegions) {
        return List.of(
                evaluateAge(policy, profile),
                evaluateRegion(profile, policyRegions),
                evaluateIncome(policy, profile),
                evaluateEmployment(policy, profile),
                evaluateHouseless(policy, profile));
    }

    private ConditionResultDTO evaluateAge(Policy policy, MemberProfileDTO profile) {
        String condition = ageCondition(policy);
        if (hasNoAgeLimit(policy)) {
            return result(EligibilityConditionType.AGE, EligibilityStatus.ABLE,
                    "제한 없음", memberAge(profile));
        }
        if (profile.birth() == null) {
            return result(EligibilityConditionType.AGE, EligibilityStatus.UNKNOWN,
                    condition, "미입력");
        }

        int age = Period.between(profile.birth(), LocalDate.now(clock)).getYears();
        boolean belowMinimum = policy.getMinAge() != null && age < policy.getMinAge();
        boolean aboveMaximum = policy.getMaxAge() != null && age > policy.getMaxAge();
        EligibilityStatus status = belowMinimum || aboveMaximum
                ? EligibilityStatus.DISABLE : EligibilityStatus.ABLE;
        if (policy.getMinAge() == null && policy.getMaxAge() == null) {
            status = EligibilityStatus.UNKNOWN;
        }
        return result(EligibilityConditionType.AGE, status, condition, "만 " + age + "세");
    }

    private ConditionResultDTO evaluateRegion(MemberProfileDTO profile, List<PolicyRegion> policyRegions) {
        if (policyRegions == null || policyRegions.isEmpty()) {
            return result(EligibilityConditionType.REGION, EligibilityStatus.ABLE,
                    "전국", profileRegion(profile));
        }
        String condition = regionCondition(policyRegions);
        if (profile.region() == null) {
            return result(EligibilityConditionType.REGION, EligibilityStatus.UNKNOWN,
                    condition, "미입력");
        }
        // 정책 지역 중 하나라도 회원 거주지(시군구)와 같으면 가능합니다.
        String memberRegionCode = profile.region().regionCode();
        boolean matches = policyRegions.stream()
                .anyMatch(policyRegion -> policyRegion.getRegion().getRegionCode().equals(memberRegionCode));
        return result(EligibilityConditionType.REGION,
                matches ? EligibilityStatus.ABLE : EligibilityStatus.DISABLE,
                condition, profileRegion(profile));
    }

    // 지역이 여러 곳이면 "서울특별시 종로구 외 24곳"처럼 첫 지역과 나머지 수로 보여 줍니다.
    private String regionCondition(List<PolicyRegion> policyRegions) {
        return RegionConverter.summarize(policyRegions.stream().map(PolicyRegion::getRegion).toList());
    }

    private ConditionResultDTO evaluateIncome(Policy policy, MemberProfileDTO profile) {
        return result(EligibilityConditionType.INCOME,
                incomeEvaluator.evaluate(policy, profile),
                incomeCondition(policy),
                profile.incomeRangeCode() == null
                        ? "미입력" : profile.incomeRangeCode().getLabel());
    }

    private ConditionResultDTO evaluateEmployment(Policy policy, MemberProfileDTO profile) {
        Set<PolicyEmploymentCondition> conditions = policy.getEmploymentCodes();
        if (conditions == null || conditions.isEmpty()) {
            return result(EligibilityConditionType.EMPLOYMENT, EligibilityStatus.UNKNOWN,
                    "정보 없음", employmentValue(profile));
        }
        if (conditions.contains(PolicyEmploymentCondition.NO_RESTRICTION)) {
            return result(EligibilityConditionType.EMPLOYMENT, EligibilityStatus.ABLE,
                    "취업 상태 제한 없음", employmentValue(profile));
        }
        EmploymentStatus memberEmployment = profile.employmentCode();
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

    private ConditionResultDTO evaluateHouseless(Policy policy, MemberProfileDTO profile) {
        PolicyHouselessRequirement requirement = policy.getHouselessRequirement();
        if (requirement == null || requirement == PolicyHouselessRequirement.UNKNOWN) {
            return result(EligibilityConditionType.HOUSELESS, EligibilityStatus.UNKNOWN,
                    "확인 필요", houselessValue(profile));
        }
        if (requirement == PolicyHouselessRequirement.NOT_REQUIRED) {
            return result(EligibilityConditionType.HOUSELESS, EligibilityStatus.ABLE,
                    "무주택 제한 없음", houselessValue(profile));
        }
        if (profile.houselessYn() == null) {
            return result(EligibilityConditionType.HOUSELESS, EligibilityStatus.UNKNOWN,
                    "무주택자", "미입력");
        }
        return result(EligibilityConditionType.HOUSELESS,
                profile.houselessYn() ? EligibilityStatus.ABLE : EligibilityStatus.DISABLE,
                "무주택자", houselessValue(profile));
    }

    private String ageCondition(Policy policy) {
        if (hasNoAgeLimit(policy)) return "제한 없음";
        if (policy.getMinAge() != null && policy.getMaxAge() != null) {
            return "만 " + policy.getMinAge() + "~" + policy.getMaxAge() + "세";
        }
        if (policy.getMinAge() != null) return "만 " + policy.getMinAge() + "세 이상";
        if (policy.getMaxAge() != null) return "만 " + policy.getMaxAge() + "세 이하";
        return "확인 필요";
    }

    private boolean hasNoAgeLimit(Policy policy) {
        if (Boolean.FALSE.equals(policy.getAgeLimitYn())) return true;
        return policy.getMinAge() != null && policy.getMinAge() <= 0
                && policy.getMaxAge() != null && policy.getMaxAge() <= 0;
    }

    private String memberAge(MemberProfileDTO profile) {
        return profile.birth() == null ? "미입력"
                : "만 " + Period.between(profile.birth(), LocalDate.now(clock)).getYears() + "세";
    }

    private String profileRegion(MemberProfileDTO profile) {
        return profile.region() == null ? "미입력" : profile.region().sigunguName();
    }

    private String incomeCondition(Policy policy) {
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

    private String employmentValue(MemberProfileDTO profile) {
        return profile.employmentCode() == null
                ? "미입력" : profile.employmentCode().getLabel();
    }

    private String houselessValue(MemberProfileDTO profile) {
        if (profile.houselessYn() == null) return "미입력";
        return profile.houselessYn() ? "무주택" : "주택 소유";
    }

    private String money(Integer value) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(value) + "원";
    }

    private ConditionResultDTO result(EligibilityConditionType type, EligibilityStatus status,
            String policyCondition, String memberValue) {
        return new ConditionResultDTO(type, status, policyCondition, memberValue);
    }
}
