package com.hyeja.policyservice.term.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hyeja.policyservice.term.dto.TermResponseDTO;
import com.hyeja.policyservice.term.entity.Term;
import com.hyeja.policyservice.term.repository.TermRepository;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

    @Mock
    private TermRepository termRepository;

    @InjectMocks
    private TermService termService;

    @Test
    void returnsConvertedTermList() {
        Term term = Term.builder()
                .term("중위소득")
                .easyDescription("전체 가구를 소득 순서로 세웠을 때 가운데 가구의 소득")
                .example("중위소득 60% 이하")
                .build();
        ReflectionTestUtils.setField(term, "termId", 1L);
        when(termRepository.findAllByDeletedAtIsNullOrderByTermIdAsc()).thenReturn(List.of(term));

        List<TermResponseDTO> result = termService.getTerms();

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getTermId()).isEqualTo(1);
            assertThat(item.getTerm()).isEqualTo("중위소득");
            assertThat(item.getEasyDescription()).isEqualTo("전체 가구를 소득 순서로 세웠을 때 가운데 가구의 소득");
            assertThat(item.getExample()).isEqualTo("중위소득 60% 이하");
        });
        verify(termRepository).findAllByDeletedAtIsNullOrderByTermIdAsc();
    }

    @Test
    void returnsTermDetail() {
        Term term = Term.builder()
                .term("중위소득")
                .easyDescription("전체 가구 소득의 중간값")
                .build();
        ReflectionTestUtils.setField(term, "termId", 1L);
        when(termRepository.findByTermIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(term));

        assertThat(termService.getTerm(1L)).satisfies(result -> {
            assertThat(result.termId()).isEqualTo(1L);
            assertThat(result.termName()).isEqualTo("중위소득");
            assertThat(result.description()).isEqualTo("전체 가구 소득의 중간값");
        });
    }

    @Test
    void rejectsMissingTerm() {
        when(termRepository.findByTermIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> termService.getTerm(99L))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorStatus.TERM_NOT_FOUND));
    }
}
