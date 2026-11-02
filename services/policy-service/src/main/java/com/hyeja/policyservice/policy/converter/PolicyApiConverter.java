package com.hyeja.policyservice.policy.converter;

import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PolicyApiConverter {
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final PolicyApiCodeConverter codeConverter;

    public Policy convert(PolicyItem item, PolicyCategory category,
            PolicyHouselessRequirement houselessRequirement,
            PolicyIncomeCondition incomeCondition, Integer incomeMin, Integer incomeMax) {
        return convert(item, Set.of(category), null, houselessRequirement,
                incomeCondition, incomeMin, incomeMax);
    }

    public Policy convert(PolicyItem item, Set<PolicyCategory> categories, String description,
            PolicyHouselessRequirement houselessRequirement,
            PolicyIncomeCondition incomeCondition, Integer incomeMin, Integer incomeMax) {
        PolicyApplyPeriod applyPeriod = codeConverter.convertApplyPeriod(item.getApplyPeriodCode());
        DateRange dates = applyPeriod == PolicyApplyPeriod.SPECIFIC_PERIOD
                ? parseDateRange(item.getApplyYmd()) : new DateRange(null, null);
        Integer minAge = parsePositiveInteger(item.getMinAge());
        Integer maxAge = parsePositiveInteger(item.getMaxAge());
        boolean hasAgeLimit = toBoolean(item.getAgeLimitYn(), false)
                && (minAge != null || maxAge != null);
        return Policy.builder()
                .policyId(trimToNull(item.getPolicyId()))
                .policyName(defaultIfBlank(item.getPolicyName(), "제목 없음"))
                .categories(categories)
                .apiSubCategory(trimToNull(item.getSubCategory()))
                .keywords(trimToNull(item.getKeywords()))
                .description(trimToNull(description))
                .supportContent(trimToNull(item.getSupportContent()))
                .minAge(minAge)
                .maxAge(maxAge)
                .ageLimitYn(hasAgeLimit)
                .incomeConditionCode(incomeCondition)
                .incomeMin(incomeMin)
                .incomeMax(incomeMax)
                .incomeEtc(trimToNull(item.getIncomeEtc()))
                .marriageCode(codeConverter.convertMarriage(item.getMarriageCode()))
                .employmentCodes(codeConverter.convertEmployment(item.getEmploymentCodes()))
                .houselessRequirement(houselessRequirement)
                .housingType(trimToNull(item.getSubCategory()))
                .applyPeriodCode(applyPeriod)
                .extraQualification(joinNonBlank(
                        item.getExtraQualification(), item.getParticipantTarget()))
                .applyStartDate(dates.start())
                .applyEndDate(dates.end())
                .applyMethod(trimToNull(item.getApplyMethod()))
                .applyUrl(trimToNull(item.getApplyUrl()))
                .refUrl(firstNonBlank(item.getReferenceUrl1(), item.getReferenceUrl2()))
                .viewCount(parseInteger(item.getViewCount(), 0))
                .activeYn(codeConverter.isApproved(item.getApprovalStatusCode()))
                .build();
    }

    private DateRange parseDateRange(String value) {
        String text = trimToNull(value);
        if (text == null) return new DateRange(null, null);
        LocalDate start = null;
        LocalDate end = null;
        for (String token : text.split("[^0-9]+")) {
            if (token.length() != 8) continue;
            try {
                LocalDate parsed = LocalDate.parse(token, BASIC_DATE);
                if (start == null) start = parsed;
                end = parsed;
            } catch (DateTimeParseException ignored) {
                // API의 비정형 날짜는 null로 두어 동기화 전체가 중단되지 않게 한다.
            }
        }
        return new DateRange(start, end);
    }

    private Integer parseNullableInteger(String value) {
        String text = trimToNull(value);
        if (text == null) return null;
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer parsePositiveInteger(String value) {
        Integer parsed = parseNullableInteger(value);
        return parsed == null || parsed <= 0 ? null : parsed;
    }

    private int parseInteger(String value, int defaultValue) {
        Integer parsed = parseNullableInteger(value);
        return parsed == null ? defaultValue : parsed;
    }

    private boolean toBoolean(String value, boolean defaultValue) {
        String text = trimToNull(value);
        return text == null ? defaultValue
                : "Y".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private String joinNonBlank(String first, String second) {
        String left = trimToNull(first);
        String right = trimToNull(second);
        if (left == null) return right;
        if (right == null || left.equals(right)) return left;
        return left + System.lineSeparator() + right;
    }

    private String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        String text = trimToNull(value);
        return text == null ? defaultValue : text;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }
}

