package com.hyeja.policyservice.region.entity;

import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class RegionTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsRegionWithoutLosingLeadingZero() {
        // 실제 행정구역 코드가 아닌 문자열 보존 검증용 코드입니다.
        Region region = Region.builder().regionCode("01234").sigunguName("테스트 지역").build();
        entityManager.persist(region);
        entityManager.flush();
        entityManager.clear();

        Region stored = entityManager.find(Region.class, "01234");
        assertThat(stored.getRegionCode()).isEqualTo("01234");
        assertThat(stored.getSigunguName()).isEqualTo("테스트 지역");
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateRegionCode() {
        entityManager.persist(Region.builder().regionCode("11110").sigunguName("종로구").build());
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> {
            entityManager.persist(Region.builder().regionCode("11110").sigunguName("중복 지역").build());
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void databaseRequiresSigunguName() {
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO region (region_code, sigungu_name, created_at, updated_at)
                VALUES ('11110', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """).executeUpdate())
                .isInstanceOf(ConstraintViolationException.class);
    }
}
