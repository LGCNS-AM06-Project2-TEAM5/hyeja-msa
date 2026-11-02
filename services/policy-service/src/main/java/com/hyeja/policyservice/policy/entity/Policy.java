package com.hyeja.policyservice.policy.entity;

import com.hyeja.policyservice.policy.converter.PolicyCategoryConverter;
import com.hyeja.policyservice.policy.converter.PolicyEmploymentConditionsConverter;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.enums.PolicyMarriageCondition;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.global.baseEntity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy extends BaseEntity {

    @Id
    @Column(name = "policy_id", nullable = false, length = 30, updatable = false)
    private String policyId;

    @Column(name = "policy_name", nullable = false, length = 200)
    private String policyName;

    @Convert(converter = PolicyCategoryConverter.class)
    @Column(name = "category", nullable = false, length = 100)
    private Set<PolicyCategory> categories;

    @Column(name = "api_sub_category", length = 50)
    private String apiSubCategory;

    @Column(name = "keywords", length = 500)
    private String keywords;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "support_content", columnDefinition = "TEXT")
    private String supportContent;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "age_limit_yn", nullable = false)
    private Boolean ageLimitYn;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_condition_code", length = 20)
    private PolicyIncomeCondition incomeConditionCode;

    @Column(name = "income_min")
    private Integer incomeMin;

    @Column(name = "income_max")
    private Integer incomeMax;

    @Column(name = "income_etc", columnDefinition = "TEXT")
    private String incomeEtc;

    @Enumerated(EnumType.STRING)
    @Column(name = "marriage_code", length = 20)
    private PolicyMarriageCondition marriageCode;

    @Convert(converter = PolicyEmploymentConditionsConverter.class)
    @Column(name = "employment_codes", length = 200)
    private Set<PolicyEmploymentCondition> employmentCodes;

    @Enumerated(EnumType.STRING)
    @Column(name = "houseless_yn", nullable = false, length = 20)
    private PolicyHouselessRequirement houselessRequirement;

    @Column(name = "housing_type", length = 20)
    private String housingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_period_code", nullable = false, length = 20)
    private PolicyApplyPeriod applyPeriodCode;

    @Column(name = "extra_qualification", columnDefinition = "TEXT")
    private String extraQualification;

    @Column(name = "apply_start_date")
    private LocalDate applyStartDate;

    @Column(name = "apply_end_date")
    private LocalDate applyEndDate;

    @Column(name = "apply_method", columnDefinition = "TEXT")
    private String applyMethod;

    @Column(name = "apply_url", length = 500)
    private String applyUrl;

    @Column(name = "ref_url", length = 500)
    private String refUrl;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    @Column(name = "active_yn", nullable = false)
    private Boolean activeYn;

    @Builder
    public Policy(String policyId, String policyName, Set<PolicyCategory> categories,
            String apiSubCategory, String keywords, String description, String supportContent,
            Integer minAge, Integer maxAge, Boolean ageLimitYn,
            PolicyIncomeCondition incomeConditionCode,
            Integer incomeMin, Integer incomeMax, String incomeEtc,
            PolicyMarriageCondition marriageCode, Set<PolicyEmploymentCondition> employmentCodes,
            PolicyHouselessRequirement houselessRequirement, String housingType,
            PolicyApplyPeriod applyPeriodCode,
            String extraQualification, LocalDate applyStartDate, LocalDate applyEndDate,
            String applyMethod, String applyUrl, String refUrl, Integer viewCount, Boolean activeYn) {
        this.policyId = policyId;
        this.policyName = policyName;
        this.categories = categories;
        this.apiSubCategory = apiSubCategory;
        this.keywords = keywords;
        this.description = description;
        this.supportContent = supportContent;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.ageLimitYn = ageLimitYn;
        this.incomeConditionCode = incomeConditionCode;
        this.incomeMin = incomeMin;
        this.incomeMax = incomeMax;
        this.incomeEtc = incomeEtc;
        this.marriageCode = marriageCode;
        this.employmentCodes = employmentCodes;
        this.houselessRequirement = houselessRequirement == null
                ? PolicyHouselessRequirement.UNKNOWN : houselessRequirement;
        this.housingType = housingType;
        this.applyPeriodCode = applyPeriodCode;
        this.extraQualification = extraQualification;
        this.applyStartDate = applyStartDate;
        this.applyEndDate = applyEndDate;
        this.applyMethod = applyMethod;
        this.applyUrl = applyUrl;
        this.refUrl = refUrl;
        this.viewCount = viewCount == null ? 0 : viewCount;
        this.activeYn = activeYn == null ? true : activeYn;
    }

}
