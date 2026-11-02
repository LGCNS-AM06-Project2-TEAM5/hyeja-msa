package com.hyeja.policyservice.policy.entity;

import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.enums.PolicyMarriageCondition;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class PolicyTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAllPolicyFieldsAndDefaultValues() {
        String id = "P".repeat(30);
        String longText = "정책 설명".repeat(200);
        Policy policy = requiredFields(id)
                .policyName("가".repeat(200))
                .apiSubCategory("임차료 지원")
                .keywords("청년,주거")
                .description(longText)
                .supportContent(longText)
                .minAge(19).maxAge(34).ageLimitYn(true)
                .incomeConditionCode(PolicyIncomeCondition.COMPARABLE)
                .incomeMin(0).incomeMax(5000).incomeEtc(longText)
                .marriageCode(PolicyMarriageCondition.MARRIED)
                .employmentCodes(java.util.Set.of(PolicyEmploymentCondition.EMPLOYED))
                .houselessRequirement(PolicyHouselessRequirement.NOT_REQUIRED).housingType("TEST")
                .extraQualification(longText)
                .applyStartDate(LocalDate.of(2026, 1, 1))
                .applyEndDate(LocalDate.of(2026, 12, 31))
                .applyMethod(longText)
                .applyUrl("https://example.com/apply")
                .refUrl("https://example.com/reference")
                .build();
        entityManager.persist(policy);
        entityManager.flush();
        entityManager.clear();

        Policy stored = entityManager.find(Policy.class, id);
        assertThat(stored.getPolicyId()).isEqualTo(id);
        assertThat(stored.getPolicyName()).hasSize(200);
        assertThat(stored.getCategories()).containsExactly(PolicyCategory.MONTHLY_RENT);
        assertThat(stored.getApiSubCategory()).isEqualTo("임차료 지원");
        assertThat(stored.getKeywords()).isEqualTo("청년,주거");
        assertThat(stored.getDescription()).isEqualTo(longText);
        assertThat(stored.getSupportContent()).isEqualTo(longText);
        assertThat(stored.getMinAge()).isEqualTo(19);
        assertThat(stored.getMaxAge()).isEqualTo(34);
        assertThat(stored.getAgeLimitYn()).isTrue();
        assertThat(stored.getIncomeConditionCode()).isEqualTo(PolicyIncomeCondition.COMPARABLE);
        assertThat(stored.getIncomeMin()).isZero();
        assertThat(stored.getIncomeMax()).isEqualTo(5000);
        assertThat(stored.getIncomeEtc()).isEqualTo(longText);
        assertThat(stored.getMarriageCode()).isEqualTo(PolicyMarriageCondition.MARRIED);
        assertThat(stored.getEmploymentCodes())
                .containsExactly(PolicyEmploymentCondition.EMPLOYED);
        assertThat(stored.getHouselessRequirement())
                .isEqualTo(PolicyHouselessRequirement.NOT_REQUIRED);
        assertThat(stored.getHousingType()).isEqualTo("TEST");
        assertThat(stored.getApplyPeriodCode()).isEqualTo(PolicyApplyPeriod.SPECIFIC_PERIOD);
        assertThat(stored.getExtraQualification()).isEqualTo(longText);
        assertThat(stored.getApplyStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(stored.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(stored.getApplyMethod()).isEqualTo(longText);
        assertThat(stored.getApplyUrl()).isEqualTo("https://example.com/apply");
        assertThat(stored.getRefUrl()).isEqualTo("https://example.com/reference");
        assertThat(stored.getViewCount()).isZero();
        assertThat(stored.getActiveYn()).isTrue();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(stored.getDeletedAt()).isNull();
    }

    @Test
    void allowsNullForOptionalConditionsAndDates() {
        entityManager.persist(requiredFields("minimal").build());
        entityManager.flush();
        entityManager.clear();

        Policy stored = entityManager.find(Policy.class, "minimal");
        assertThat(stored.getAgeLimitYn()).isFalse();
        assertThat(stored.getMinAge()).isNull();
        assertThat(stored.getMaxAge()).isNull();
        assertThat(stored.getIncomeMin()).isNull();
        assertThat(stored.getIncomeMax()).isNull();
        assertThat(stored.getHouselessRequirement())
                .isEqualTo(PolicyHouselessRequirement.UNKNOWN);
        assertThat(stored.getEmploymentCodes()).isNull();
        assertThat(stored.getApplyStartDate()).isNull();
        assertThat(stored.getApplyEndDate()).isNull();
    }

    @Test
    void rejectsDuplicatePolicyId() {
        entityManager.persist(requiredFields("duplicate").build());
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> {
            entityManager.persist(requiredFields("duplicate").build());
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"policy_name", "category", "age_limit_yn", "houseless_yn",
            "apply_period_code", "view_count", "active_yn"})
    void databaseRejectsMissingRequiredColumn(String missingColumn) {
        String[] columns = {"policy_name", "category", "age_limit_yn", "houseless_yn",
                "apply_period_code", "view_count", "active_yn"};
        String[] values = {"'정책'", "'MONTHLY_RENT'", "false", "'UNKNOWN'",
                "'SPECIFIC_PERIOD'", "0", "true"};
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(missingColumn)) {
                values[i] = "NULL";
            }
        }
        String sql = "INSERT INTO policy (policy_id, " + String.join(",", columns)
                + ", created_at, updated_at) VALUES ('required', " + String.join(",", values)
                + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        assertThatThrownBy(() -> entityManager.createNativeQuery(sql).executeUpdate())
                .isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @EnumSource(PolicyCategory.class)
    void storesEveryCategoryByName(PolicyCategory category) {
        String id = "category-" + category.name();
        entityManager.persist(requiredFields(id).categories(java.util.Set.of(category)).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Policy.class, id).getCategories()).containsExactly(category);
        assertThat(entityManager.createNativeQuery("SELECT category FROM policy WHERE policy_id = :id")
                .setParameter("id", id).getSingleResult()).isEqualTo(category.name());
    }

    @Test
    void storesMultipleCategoriesInOnePolicy() {
        String id = "multiple-categories";
        entityManager.persist(requiredFields(id)
                .categories(java.util.Set.of(
                        PolicyCategory.MONTHLY_RENT, PolicyCategory.PUBLIC_RENT))
                .build());
        entityManager.flush();
        entityManager.clear();

        Policy stored = entityManager.find(Policy.class, id);
        assertThat(stored.getCategories()).containsExactlyInAnyOrder(
                PolicyCategory.MONTHLY_RENT, PolicyCategory.PUBLIC_RENT);
        assertThat(entityManager.createNativeQuery(
                        "SELECT category FROM policy WHERE policy_id = :id")
                .setParameter("id", id).getSingleResult())
                .isEqualTo("MONTHLY_RENT,PUBLIC_RENT");
    }

    private Policy.PolicyBuilder requiredFields(String id) {
        return Policy.builder().policyId(id).policyName("테스트 정책")
                .categories(java.util.Set.of(PolicyCategory.MONTHLY_RENT))
                .ageLimitYn(false).applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD);
    }
}
