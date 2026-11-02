package com.hyeja.policyservice.term.entity;

import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
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
class TermTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsTermAtMaximumColumnLengths() {
        Term term = Term.builder().term("가".repeat(50)).easyDescription("나".repeat(500))
                .example("다".repeat(300)).build();
        entityManager.persist(term);
        entityManager.flush();
        entityManager.clear();

        Term stored = entityManager.find(Term.class, term.getTermId());
        assertThat(stored.getTermId()).isNotNull();
        assertThat(stored.getTerm()).isEqualTo("가".repeat(50));
        assertThat(stored.getEasyDescription()).isEqualTo("나".repeat(500));
        assertThat(stored.getExample()).isEqualTo("다".repeat(300));
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(stored.getDeletedAt()).isNull();
    }

    @Test
    void allowsMissingExample() {
        Term term = Term.builder().term("무주택자").easyDescription("본인 명의의 주택이 없는 사람").build();
        entityManager.persist(term);
        entityManager.flush();
        entityManager.clear();

        Term stored = entityManager.find(Term.class, term.getTermId());
        assertThat(stored.getExample()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"term", "description"})
    void databaseRejectsNullRequiredField(String missing) {
        String term = missing.equals("term") ? null : "무주택자";
        String description = missing.equals("description") ? null : "본인 명의의 주택이 없는 사람";

        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                INSERT INTO term (term, easy_description, created_at, updated_at)
                VALUES (:term, :description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)
                .setParameter("term", term)
                .setParameter("description", description)
                .executeUpdate())
                .isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"term", "description", "example"})
    void databaseRejectsColumnLengthOverflow(String field) {
        Term term = Term.builder()
                .term("가".repeat(field.equals("term") ? 51 : 50))
                .easyDescription("나".repeat(field.equals("description") ? 501 : 500))
                .example("다".repeat(field.equals("example") ? 301 : 300))
                .build();

        assertThatThrownBy(() -> {
            entityManager.persist(term);
            entityManager.flush();
        }).isInstanceOf(DataException.class);
    }
}
