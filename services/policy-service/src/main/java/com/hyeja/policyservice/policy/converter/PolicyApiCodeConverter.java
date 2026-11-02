package com.hyeja.policyservice.policy.converter;

import com.hyeja.policyservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyMarriageCondition;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PolicyApiCodeConverter {
    private static final String APPROVED_STATUS_CODE = "44002";

    public boolean isApproved(String apiCode) {
        return APPROVED_STATUS_CODE.equals(normalize(apiCode));
    }

    public PolicyMarriageCondition convertMarriage(String apiCode) {
        String code = normalize(apiCode);
        if (code == null) return null;

        return switch (code) {
            case "55001" -> PolicyMarriageCondition.MARRIED;
            case "55002" -> PolicyMarriageCondition.SINGLE;
            case "55003" -> PolicyMarriageCondition.NO_RESTRICTION;
            default -> null;
        };
    }

    public Set<PolicyEmploymentCondition> convertEmployment(String apiCodes) {
        if (apiCodes == null || apiCodes.isBlank()) return Set.of();

        return Arrays.stream(apiCodes.split(","))
                .map(this::convertSingleEmployment)
                .filter(value -> value != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    public PolicyApplyPeriod convertApplyPeriod(String apiCode) {
        String code = normalize(apiCode);
        if (code == null) {
            throw new IllegalArgumentException("신청기간 분류코드가 없습니다.");
        }
        return switch (code) {
            case "57001" -> PolicyApplyPeriod.SPECIFIC_PERIOD;
            case "57002" -> PolicyApplyPeriod.ALWAYS;
            case "57003" -> PolicyApplyPeriod.CLOSED;
            default -> throw new IllegalArgumentException("알 수 없는 신청기간 분류코드: " + apiCode);
        };
    }

    private PolicyEmploymentCondition convertSingleEmployment(String apiCode) {
        String code = normalize(apiCode);
        if (code == null) return null;

        return switch (code) {
            case "13001" -> PolicyEmploymentCondition.EMPLOYED;
            case "13002" -> PolicyEmploymentCondition.SELF_EMPLOYED;
            case "13003" -> PolicyEmploymentCondition.UNEMPLOYED;
            case "13004" -> PolicyEmploymentCondition.FREELANCER;
            case "13005" -> PolicyEmploymentCondition.DAILY_WORKER;
            case "13006" -> PolicyEmploymentCondition.ENTREPRENEUR;
            case "13007" -> PolicyEmploymentCondition.SHORT_TERM_WORKER;
            case "13008" -> PolicyEmploymentCondition.FARMER;
            case "13009" -> PolicyEmploymentCondition.OTHER;
            case "13010" -> PolicyEmploymentCondition.NO_RESTRICTION;
            default -> null;
        };
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replaceFirst("^0+", "");
    }
}

