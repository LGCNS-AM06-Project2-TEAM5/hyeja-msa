package com.hyeja.policyservice.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.*;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.region.entity.Region;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class PolicyCandidateRepositoryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    @Autowired private PolicyRepository policies;
    @Autowired private EntityManager em;

    @Test
    void preservesLifecycleFiltersAndInclusiveDateBoundaries() {
        persist(policy("boundary").applyStartDate(TODAY).applyEndDate(TODAY));
        persist(policy("always"));
        persist(policy("expired").applyEndDate(TODAY.minusDays(1)));
        persist(policy("future").applyStartDate(TODAY.plusDays(1)));
        persist(policy("inactive").activeYn(false));
        persist(policy("closed").applyPeriodCode(PolicyApplyPeriod.CLOSED));
        persist(policy("deleted")).softDelete();
        assertThat(search(true, false)).extracting(Policy::getPolicyId)
                .containsExactly("boundary", "always");
    }

    @Test
    void includesNationwideExactAndLegacySidoButExcludesOtherRegions() {
        Region mapo = region("11440");
        Region gangnam = region("11680");
        Region seoul = region("11000");
        Region busan = region("26000");
        persist(policy("nationwide"));
        link(persist(policy("exact")), mapo);
        link(persist(policy("sido")), seoul);
        link(persist(policy("other-district")), gangnam);
        link(persist(policy("other-sido")), busan);
        Policy multi = persist(policy("multi"));
        link(multi, mapo);
        link(multi, gangnam);
        assertThat(search(true, false)).extracting(Policy::getPolicyId)
                .containsExactly("exact", "multi", "nationwide", "sido");
    }

    @Test
    void ignoresDeletedLinksAndDeletedRegionsWhenMatchingNationwide() {
        Region mapo = region("11440");
        Region gangnam = region("11680");
        Region removed = region("26110");
        removed.softDelete();
        link(persist(policy("removed-link")), gangnam).softDelete();
        link(persist(policy("removed-region")), removed);
        Policy wrong = persist(policy("deleted-match"));
        link(wrong, mapo).softDelete();
        link(wrong, gangnam);
        assertThat(search(true, false)).extracting(Policy::getPolicyId)
                .containsExactly("removed-link", "removed-region");
    }

    @Test
    void matchesAnyRequestedCategoryAsCompleteToken() {
        persist(policy("rent"));
        persist(policy("jeonse").categories(Set.of(PolicyCategory.JEONSE)));
        persist(policy("multi").categories(Set.of(PolicyCategory.OTHER, PolicyCategory.JEONSE)));
        persist(policy("other").categories(Set.of(PolicyCategory.OTHER)));
        persist(policy("lookalike"));
        em.flush();
        em.createNativeQuery("update policy set category = 'XMONTHLY_RENT' where policy_id = 'lookalike'")
                .executeUpdate();
        em.clear();
        assertThat(search(true, true)).extracting(Policy::getPolicyId)
                .containsExactly("jeonse", "multi", "rent");
    }

    @Test
    void ordersDeadlinesNullsLastThenIdsBeforeLimitingToTwenty() {
        for (int i = 24; i >= 0; i--) {
            persist(policy("dated-%02d".formatted(i)).applyEndDate(TODAY.plusDays(2)));
        }
        persist(policy("earliest").applyEndDate(TODAY));
        persist(policy("always"));
        List<Policy> result = search(true, false);
        assertThat(result).hasSize(20);
        assertThat(result.get(0).getPolicyId()).isEqualTo("earliest");
        assertThat(result.subList(1, 20)).extracting(Policy::getPolicyId)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 19)
                        .mapToObj(i -> "dated-%02d".formatted(i)).toList());
    }

    @Test
    void doesNotFilterAgeIncomeEmploymentOrHouselessConditions() {
        persist(policy("restricted").ageLimitYn(true).minAge(60).maxAge(70)
                .incomeConditionCode(PolicyIncomeCondition.COMPARABLE).incomeMin(100).incomeMax(200)
                .employmentCodes(Set.of(PolicyEmploymentCondition.UNEMPLOYED))
                .houselessRequirement(PolicyHouselessRequirement.REQUIRED));
        assertThat(search(true, false)).extracting(Policy::getPolicyId).containsExactly("restricted");
    }

    private List<Policy> search(boolean rent, boolean jeonse) {
        em.flush();
        em.clear();
        return policies.searchActivePolicies(rent, jeonse, false, false, false,
                "11440", TODAY, PageRequest.of(0, 20, PolicySort.DEADLINE.toSort()));
    }

    private Policy.PolicyBuilder policy(String id) {
        return Policy.builder().policyId(id).policyName(id)
                .categories(Set.of(PolicyCategory.MONTHLY_RENT))
                .ageLimitYn(false).applyPeriodCode(PolicyApplyPeriod.ALWAYS);
    }

    private Policy persist(Policy.PolicyBuilder builder) {
        Policy p = builder.build();
        em.persist(p);
        return p;
    }

    private Region region(String code) {
        Region region = Region.builder().regionCode(code).sigunguName(code).build();
        em.persist(region);
        return region;
    }

    private PolicyRegion link(Policy policy, Region region) {
        PolicyRegion link = PolicyRegion.builder().policy(policy).region(region).build();
        em.persist(link);
        return link;
    }
}
