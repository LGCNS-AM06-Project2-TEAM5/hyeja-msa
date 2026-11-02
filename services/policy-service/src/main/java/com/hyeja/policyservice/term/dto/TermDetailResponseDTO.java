package com.hyeja.policyservice.term.dto;

public record TermDetailResponseDTO(
        Long termId,
        String termName,
        String description) {
}
