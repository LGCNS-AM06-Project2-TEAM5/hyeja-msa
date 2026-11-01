package com.hyeja.policyservice.policy.entity;

import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
class PolicyRegionTest {

    @Autowired
    private EntityManager entityManager;

    private Policy policy;
    private Region region;

    @BeforeEach
    void setUp() {
        policy = persistPolicy("policy-1");
        region = persistRegion("01234");
        entityManager.flush();
    }

    @Test
    void persistsCompositeKeyAndLazyAssociations() {
        entityManager.persist(PolicyRegion.builder().policy(policy).region(region).build());
        entityManager.flush();
        entityManager.clear();

        PolicyRegionId key = new PolicyRegionId("policy-1", "01234");
        PolicyRegion stored = entityManager.find(PolicyRegion.class, key);
        assertThat(stored.getId()).isEqualTo(key);
        assertThat(stored.getId().hashCode()).isEqualTo(key.hashCode());
        assertThat(stored.getId()).isNotEqualTo(new PolicyRegionId("policy-1", "11110"));
        var persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(persistenceUnitUtil.isLoaded(stored, "policy")).isFalse();
        assertThat(persistenceUnitUtil.isLoaded(stored, "region")).isFalse();
        assertThat(stored.getPolicy().getPolicyName()).isEqualTo("테스트 정책");
        assertThat(stored.getRegion().getSigunguName()).isEqualTo("테스트 지역");
        assertThat(stored.getRegion().getRegionCode()).isEqualTo("01234");
        assertThat(persistenceUnitUtil.isLoaded(stored, "policy")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(stored, "region")).isTrue();
    }

    @Test
    void supportsMultipleRegionsPerPolicyAndPoliciesPerRegion() {
        Policy secondPolicy = persistPolicy("policy-2");
        Region secondRegion = persistRegion("11110");
        entityManager.persist(PolicyRegion.builder().policy(policy).region(region).build());
        entityManager.persist(PolicyRegion.builder().policy(policy).region(secondRegion).build());
        entityManager.persist(PolicyRegion.builder().policy(secondPolicy).region(region).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.createQuery(
                "select count(pr) from PolicyRegion pr where pr.policy.policyId = :id", Long.class)
                .setParameter("id", "policy-1").getSingleResult()).isEqualTo(2L);
        assertThat(entityManager.createQuery(
                "select count(pr) from PolicyRegion pr where pr.region.regionCode = :code", Long.class)
                .setParameter("code", "01234").getSingleResult()).isEqualTo(2L);
    }

    @Test
    void rejectsDuplicatePolicyRegionPair() {
        entityManager.persist(PolicyRegion.builder().policy(policy).region(region).build());
        entityManager.flush();
        entityManager.clear();
        Policy storedPolicy = entityManager.find(Policy.class, "policy-1");
        Region storedRegion = entityManager.find(Region.class, "01234");

        assertThatThrownBy(() -> {
            entityManager.persist(PolicyRegion.builder().policy(storedPolicy).region(storedRegion).build());
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"policy", "region"})
    void databaseRejectsMissingReferencedEntity(String missing) {
        String policyId = missing.equals("policy") ? "missing-policy" : "policy-1";
        String regionCode = missing.equals("region") ? "99999" : "01234";
        String expectedConstraint = missing.equals("policy")
                ? "FK_POLICY_REGION_POLICY" : "FK_POLICY_REGION_REGION";

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO policy_region (policy_id, region_code, created_at, updated_at)
                VALUES (:policyId, :regionCode, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .setParameter("policyId", policyId)
                .setParameter("regionCode", regionCode)
                .executeUpdate())
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining(expectedConstraint);
    }

    @Test
    void deletingLinkDoesNotDeleteSharedPolicyOrRegion() {
        PolicyRegion link = PolicyRegion.builder().policy(policy).region(region).build();
        entityManager.persist(link);
        entityManager.flush();
        entityManager.remove(link);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(PolicyRegion.class, new PolicyRegionId("policy-1", "01234")))
                .isNull();
        assertThat(entityManager.find(Policy.class, "policy-1")).isNotNull();
        assertThat(entityManager.find(Region.class, "01234")).isNotNull();
    }

    private Policy persistPolicy(String id) {
        Policy value = Policy.builder().policyId(id).policyName("테스트 정책")
                .categories(java.util.Set.of(PolicyCategory.OTHER))
                .ageLimitYn(false).applyPeriodCode(
                        com.hyeja.policyservice.policy.enums.PolicyApplyPeriod.SPECIFIC_PERIOD).build();
        entityManager.persist(value);
        return value;
    }

    private Region persistRegion(String code) {
        Region value = Region.builder().regionCode(code).sigunguName("테스트 지역").build();
        entityManager.persist(value);
        return value;
    }
}
