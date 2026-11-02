package com.hyeja.policyservice.policy.converter;

import com.hyeja.policyservice.policy.enums.PolicyCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class PolicyCategoryConverter implements AttributeConverter<Set<PolicyCategory>, String> {

    private static final String LEGACY_HOUSING_CATEGORY = "주거";

    @Override
    public String convertToDatabaseColumn(Set<PolicyCategory> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return categories.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<PolicyCategory> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return Set.of();
        if (LEGACY_HOUSING_CATEGORY.equals(value)) {
            return Set.of(PolicyCategory.OTHER);
        }
        Set<PolicyCategory> categories = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(category -> !category.isEmpty())
                .map(PolicyCategory::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(categories);
    }
}
