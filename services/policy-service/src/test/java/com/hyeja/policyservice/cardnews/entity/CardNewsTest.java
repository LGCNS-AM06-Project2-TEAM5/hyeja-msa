package com.hyeja.policyservice.cardnews.entity;

import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class CardNewsTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CardNewsRepository cardNewsRepository;

    private Policy policy;

    @BeforeEach
    void setUp() {
        policy = persistPolicy("policy-1");
        entityManager.flush();
    }

    @Test
    void persistsFourCardsAndLoadsPolicyLazily() {
        for (long no = 1; no <= 4; no++) {
            entityManager.persist(CardNews.builder()
                    .policy(policy).body("가".repeat(500)).cardNo(no).build());
        }
        entityManager.flush();
        entityManager.clear();

        var cards = entityManager.createQuery(
                "select c from CardNews c where c.policy.policyId = :id order by c.cardNo", CardNews.class)
                .setParameter("id", "policy-1").getResultList();
        assertThat(cards).extracting(CardNews::getCardNo).containsExactly(1L, 2L, 3L, 4L);
        assertThat(cards).allSatisfy(card -> {
            assertThat(card.getCardNewsId()).isNotNull();
            assertThat(card.getBody()).hasSize(500);
            assertThat(card.getCreatedAt()).isNotNull();
        });
        var persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(persistenceUnitUtil.isLoaded(cards.get(0), "policy")).isFalse();
        assertThat(cards.get(0).getPolicy().getPolicyName()).isEqualTo("테스트 정책");
        assertThat(persistenceUnitUtil.isLoaded(cards.get(0), "policy")).isTrue();
    }

    @Test
    void findsActivePopupCardsInCardNumberOrder() {
        CardNews second = CardNews.builder()
                .policy(policy).body("신청 대상").cardNo(2L).build();
        CardNews first = CardNews.builder()
                .policy(policy).title("정책명").body("정책 소개").cardNo(1L).build();
        entityManager.persist(second);
        entityManager.persist(first);
        entityManager.flush();
        entityManager.clear();

        assertThat(cardNewsRepository.findAllActiveByPolicyIdOrderByCardNo("policy-1"))
                .extracting(CardNews::getCardNo)
                .containsExactly(1L, 2L);
    }

    @Test
    void sameCardNumberCanBeUsedByDifferentPolicies() {
        Policy secondPolicy = persistPolicy("policy-2");
        entityManager.persist(CardNews.builder().policy(policy).body("첫 정책").cardNo(1L).build());
        entityManager.persist(CardNews.builder().policy(secondPolicy).body("둘째 정책").cardNo(1L).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.createQuery("select count(c) from CardNews c where c.cardNo = 1", Long.class)
                .getSingleResult()).isEqualTo(2L);
    }

    @Test
    void rejectsDuplicateCardNumberWithinSamePolicy() {
        entityManager.persist(CardNews.builder().policy(policy).body("첫 카드").cardNo(1L).build());
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(CardNews.builder().policy(policy).body("중복 카드").cardNo(1L).build());
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("UK_CARD_NEWS_POLICY_CARD_NO");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 5L})
    void databaseRejectsOutOfRangeCardNumber(long cardNo) {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO card_news (policy_id, body, card_no, created_at, updated_at)
                VALUES ('policy-1', '테스트 카드', :cardNo, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).setParameter("cardNo", cardNo).executeUpdate())
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("CK_CARD_NEWS_CARD_NO");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, 5L})
    void builderRejectsMissingOrInvalidCardNumber(Long cardNo) {
        assertThatThrownBy(() -> CardNews.builder().policy(policy).body("테스트 카드").cardNo(cardNo).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void databaseRejectsMissingPolicy() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO card_news (policy_id, body, card_no, created_at, updated_at)
                VALUES ('missing-policy', '테스트 카드', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).executeUpdate())
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("FK_CARD_NEWS_POLICY");
    }

    @Test
    void databaseRejectsMissingBody() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO card_news (policy_id, body, card_no, created_at, updated_at)
                VALUES ('policy-1', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).executeUpdate())
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void deletingCardDoesNotDeletePolicy() {
        CardNews card = CardNews.builder().policy(policy).body("삭제할 카드").cardNo(1L).build();
        entityManager.persist(card);
        entityManager.flush();
        entityManager.remove(card);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(CardNews.class, card.getCardNewsId())).isNull();
        assertThat(entityManager.find(Policy.class, "policy-1")).isNotNull();
    }

    @Test
    void homeCardsExcludeClosedAndExpiredPolicies() {
        Policy closed = persistPolicy("closed", PolicyApplyPeriod.CLOSED,
                java.time.LocalDate.now().plusDays(1));
        Policy expired = persistPolicy("expired", PolicyApplyPeriod.SPECIFIC_PERIOD,
                java.time.LocalDate.now().minusDays(1));
        entityManager.persist(CardNews.builder().policy(policy).body("노출").cardNo(1L).build());
        entityManager.persist(CardNews.builder().policy(closed).body("마감").cardNo(1L).build());
        entityManager.persist(CardNews.builder().policy(expired).body("기한 지남").cardNo(1L).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(cardNewsRepository.findGuestHomeCardNews(
                java.time.LocalDate.now(), PageRequest.of(0, 4)))
                .extracting(card -> card.getPolicy().getPolicyId())
                .containsExactly("policy-1");
    }

    private Policy persistPolicy(String id) {
        return persistPolicy(id, PolicyApplyPeriod.SPECIFIC_PERIOD, null);
    }

    private Policy persistPolicy(String id, PolicyApplyPeriod applyPeriod, java.time.LocalDate applyEndDate) {
        Policy value = Policy.builder().policyId(id).policyName("테스트 정책")
                .categories(java.util.Set.of(PolicyCategory.OTHER))
                .ageLimitYn(false).applyPeriodCode(applyPeriod)
                .applyEndDate(applyEndDate).build();
        entityManager.persist(value);
        return value;
    }
}
