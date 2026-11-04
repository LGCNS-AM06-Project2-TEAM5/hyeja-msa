package com.hyeja.policyservice.term.converter;

import com.hyeja.policyservice.term.dto.TermResponseDTO;
import com.hyeja.policyservice.term.entity.Term;

public final class TermConverter {

    private TermConverter() {
    }

    public static TermResponseDTO toResponseDTO(Term term) {
        return TermResponseDTO.builder()
                .termId(term.getTermId())
                .term(term.getTerm())
                .easyDescription(term.getEasyDescription())
                .example(term.getExample())
                .build();
    }
}
