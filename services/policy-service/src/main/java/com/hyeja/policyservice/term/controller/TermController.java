package com.hyeja.policyservice.term.controller;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.term.dto.TermDetailResponseDTO;
import com.hyeja.policyservice.term.dto.TermResponseDTO;
import com.hyeja.policyservice.term.service.TermService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 정책 용어 풀이 API
@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermController {

    private final TermService termService;

    // 용어 풀이 목록: 정책 화면에서 어려운 용어를 설명하는 데 쓸 전체 용어 풀이를 반환합니다.
    @GetMapping("")
    public ApiResponse<List<TermResponseDTO>> getTerms() {
        return ApiResponse.onSuccess(termService.getTerms());
    }

    // 용어 상세: 선택한 정책 용어의 쉬운 설명을 반환합니다. 없거나 삭제된 용어면 404(TERM_001)입니다.
    @GetMapping("/{termId}")
    public ApiResponse<TermDetailResponseDTO> getTerm(
            @PathVariable("termId") Long termId) {
        return ApiResponse.onSuccess(termService.getTerm(termId));
    }
}
