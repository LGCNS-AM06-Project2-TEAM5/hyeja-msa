package com.hyeja.policyservice.policy.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// 11-04 develop 실제 수집에서 온통청년 중분류(mclsfNm)가 20자를 넘어 "청년월세 지원" 등 4건이 저장되지 않았습니다.
// housing_type은 api_sub_category와 같은 원문을 담으므로 같은 길이(50)까지 저장돼야 합니다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class PolicyHousingTypeColumnTest {

    @Autowired private EntityManager entityManager;

    @Test
    void housingTypeKeepsSubCategoryUpToApiSubCategoryLength() {
        String subCategory = "가".repeat(50);
        Policy policy = Policy.builder()
                .policyId("housing-type-50")
                .policyName("긴 중분류 정책")
                .categories(Set.of(PolicyCategory.MONTHLY_RENT))
                .ageLimitYn(false)
                .applyPeriodCode(PolicyApplyPeriod.ALWAYS)
                .activeYn(true)
                .apiSubCategory(subCategory)
                .housingType(subCategory)
                .build();

        entityManager.persist(policy);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Policy.class, "housing-type-50").getHousingType())
                .isEqualTo(subCategory);
    }
}
