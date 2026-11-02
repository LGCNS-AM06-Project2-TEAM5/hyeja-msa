package com.hyeja.policyservice.policy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicySort;
import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class PolicyQueryRepositoryTest {
    @Autowired private PolicyRepository policyRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void filtersAndPaginatesGuestPoliciesWithAlwaysOpenPoliciesLast() {
        LocalDate today = LocalDate.of(2026, 9, 27);
        persistGuestPolicy("soon", "마감 임박", PolicyCategory.MONTHLY_RENT,
                today.plusDays(1), 10, true);
        persistGuestPolicy("later", "마감 여유", PolicyCategory.MONTHLY_RENT,
                today.plusDays(5), 20, true);
        persistGuestPolicy("always", "상시 모집", PolicyCategory.MONTHLY_RENT,
                null, 30, true);
        persistGuestPolicy("expired", "마감됨", PolicyCategory.MONTHLY_RENT,
                today.minusDays(1), 40, true);
        persistGuestPolicy("inactive", "비활성", PolicyCategory.MONTHLY_RENT,
                today.plusDays(2), 50, false);
        Policy deleted = persistGuestPolicy(
                "deleted", "삭제됨", PolicyCategory.MONTHLY_RENT,
                today.plusDays(3), 60, true);
        deleted.softDelete();
        persistGuestPolicy("other-category", "전세 정책", PolicyCategory.JEONSE,
                today.plusDays(1), 70, true);
        persistGuestPolicy("closed-code", "종료 코드 정책", PolicyCategory.MONTHLY_RENT,
                null, 80, true, PolicyApplyPeriod.CLOSED);
        Policy future = persistGuestPolicy(
                "future", "접수 예정", PolicyCategory.MONTHLY_RENT,
                today.plusDays(10), 90, true);
        ReflectionTestUtils.setField(future, "applyStartDate", today.plusDays(1));
        entityManager.flush();
        entityManager.clear();

        var firstPage = policyRepository.findGuestHousingPolicies(
                PolicyCategory.MONTHLY_RENT.name(),
                today,
                PageRequest.of(0, 2, PolicySort.DEADLINE.toSort())
        );
        var secondPage = policyRepository.findGuestHousingPolicies(
                PolicyCategory.MONTHLY_RENT.name(),
                today,
                PageRequest.of(1, 2, PolicySort.DEADLINE.toSort())
        );

        assertThat(firstPage.getContent())
                .extracting(Policy::getPolicyId)
                .containsExactly("soon", "later");
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.getContent())
                .extracting(Policy::getPolicyId)
                .containsExactly("always");
    }

    @Test
    void appliesGuestViewCountAndNameSortsWithStablePolicyIdOrder() {
        LocalDate today = LocalDate.of(2026, 9, 27);
        persistGuestPolicy("alpha", "Alpha", PolicyCategory.MONTHLY_RENT,
                today.plusDays(2), 20, true);
        persistGuestPolicy("beta", "Beta", PolicyCategory.JEONSE,
                today.plusDays(2), 10, true);
        persistGuestPolicy("gamma", "Gamma", PolicyCategory.OTHER,
                today.plusDays(2), 20, true);
        entityManager.flush();
        entityManager.clear();

        var byViewCount = policyRepository.findGuestHousingPolicies(
                null,
                today,
                PageRequest.of(0, 8, PolicySort.VIEW_COUNT.toSort())
        );
        var byName = policyRepository.findGuestHousingPolicies(
                null,
                today,
                PageRequest.of(0, 8, PolicySort.NAME.toSort())
        );

        assertThat(byViewCount.getContent())
                .extracting(Policy::getPolicyId)
                .containsExactly("alpha", "gamma", "beta");
        assertThat(byName.getContent())
                .extracting(Policy::getPolicyId)
                .containsExactly("alpha", "beta", "gamma");
    }

    private Policy persistGuestPolicy(
            String policyId,
            String policyName,
            PolicyCategory category,
            LocalDate applyEndDate,
            int viewCount,
            boolean active
    ) {
        return persistGuestPolicy(
                policyId,
                policyName,
                category,
                applyEndDate,
                viewCount,
                active,
                applyEndDate == null ? PolicyApplyPeriod.ALWAYS : PolicyApplyPeriod.SPECIFIC_PERIOD
        );
    }

    private Policy persistGuestPolicy(
            String policyId,
            String policyName,
            PolicyCategory category,
            LocalDate applyEndDate,
            int viewCount,
            boolean active,
            PolicyApplyPeriod applyPeriodCode
    ) {
        Policy policy = Policy.builder()
                .policyId(policyId)
                .policyName(policyName)
                .categories(java.util.Set.of(category))
                .ageLimitYn(false)
                .applyPeriodCode(applyPeriodCode)
                .applyEndDate(applyEndDate)
                .viewCount(viewCount)
                .activeYn(active)
                .build();
        entityManager.persist(policy);
        return policy;
    }


}
