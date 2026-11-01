package com.hyeja.policyservice.term.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.hyeja.policyservice.term.entity.Term;
import com.hyeja.policyservice.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class TermRepositoryTest {

    @Autowired
    private TermRepository termRepository;

    @Test
    void returnsOnlyActiveTermsInIdOrder() {
        Term first = termRepository.save(Term.builder()
                .term("중위소득")
                .easyDescription("전체 가구를 소득 순서로 세웠을 때 가운데 가구의 소득")
                .example("중위소득 60% 이하")
                .build());
        Term deleted = termRepository.save(Term.builder()
                .term("삭제 용어")
                .easyDescription("조회되면 안 되는 설명")
                .build());
        Term last = termRepository.save(Term.builder()
                .term("무주택자")
                .easyDescription("본인 명의 주택을 소유하지 않은 사람")
                .build());
        deleted.softDelete();
        termRepository.flush();

        List<Term> result = termRepository.findAllByDeletedAtIsNullOrderByTermIdAsc();

        assertThat(result)
                .extracting(Term::getTermId, Term::getTerm)
                .containsExactly(
                        tuple(first.getTermId(), "중위소득"),
                        tuple(last.getTermId(), "무주택자")
                );
    }
}
