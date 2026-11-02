package com.hyeja.policyservice.policy.converter;

import com.hyeja.policyservice.policy.enums.PolicyEmploymentCondition;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class PolicyEmploymentConditionsConverter
        implements AttributeConverter<Set<PolicyEmploymentCondition>, String> {

    @Override
    public String convertToDatabaseColumn(Set<PolicyEmploymentCondition> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<PolicyEmploymentCondition> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(PolicyEmploymentCondition::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
