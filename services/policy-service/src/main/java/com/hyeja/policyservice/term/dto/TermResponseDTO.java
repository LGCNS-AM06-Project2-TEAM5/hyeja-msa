package com.hyeja.policyservice.term.dto;

import lombok.Builder;
import lombok.Getter;

// 정책 용어 풀이
@Getter
@Builder
public class TermResponseDTO {

    // 용어 ID, 예: 1
    private Long termId;

    // 정책 용어, 예: "중위소득"
    private String term;

    // 쉬운 설명
    private String easyDescription;

    // 예시 문장, 예: "중위소득 60% 이하인 가구". 없으면 null
    private String example;
}
