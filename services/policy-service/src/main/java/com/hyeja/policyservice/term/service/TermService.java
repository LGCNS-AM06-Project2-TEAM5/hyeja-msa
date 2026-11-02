package com.hyeja.policyservice.term.service;

import com.hyeja.policyservice.term.converter.TermConverter;
import com.hyeja.policyservice.term.dto.TermResponseDTO;
import com.hyeja.policyservice.term.dto.TermDetailResponseDTO;
import com.hyeja.policyservice.term.repository.TermRepository;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermService {

    private final TermRepository termRepository;

    public List<TermResponseDTO> getTerms() {
        return termRepository.findAllByDeletedAtIsNullOrderByTermIdAsc().stream()
                .map(TermConverter::toResponseDTO)
                .toList();
    }

    public TermDetailResponseDTO getTerm(Long termId) {
        return termRepository.findByTermIdAndDeletedAtIsNull(termId)
                .map(term -> new TermDetailResponseDTO(
                        term.getTermId(), term.getTerm(), term.getEasyDescription()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.TERM_NOT_FOUND));
    }
}
