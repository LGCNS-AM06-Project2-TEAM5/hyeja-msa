package com.hyeja.policyservice.policy.converter;

import com.hyeja.policyservice.policy.enums.PolicyCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCategoryConverterTest {

    private final PolicyCategoryConverter converter = new PolicyCategoryConverter();

    @Test
    void convertsLegacyHousingCategoryToOther() {
        assertThat(converter.convertToEntityAttribute("주거"))
                .containsExactly(PolicyCategory.OTHER);
    }

    @ParameterizedTest
    @EnumSource(PolicyCategory.class)
    void convertsCurrentCategoryNamesBothWays(PolicyCategory category) {
        assertThat(converter.convertToDatabaseColumn(java.util.Set.of(category)))
                .isEqualTo(category.name());
        assertThat(converter.convertToEntityAttribute(category.name())).containsExactly(category);
    }

    @Test
    void convertsMultipleCategoriesBothWays() {
        var categories = java.util.Set.of(
                PolicyCategory.MONTHLY_RENT, PolicyCategory.PUBLIC_RENT);

        assertThat(converter.convertToDatabaseColumn(categories))
                .isEqualTo("MONTHLY_RENT,PUBLIC_RENT");
        assertThat(converter.convertToEntityAttribute("MONTHLY_RENT,PUBLIC_RENT"))
                .containsExactlyInAnyOrderElementsOf(categories);
    }
}
