package com.hyeja.recommendationservice.policy.controller;

import com.hyeja.recommendationservice.global.apiPayload.ApiResponse;
import com.hyeja.recommendationservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.recommendationservice.global.exception.GeneralException;
import com.hyeja.recommendationservice.policy.dto.PolicySearchResponseDTO;
import com.hyeja.recommendationservice.policy.service.PolicySearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recommendation.search.enabled", havingValue = "true")
public class PolicySearchController {
    // Gateway가 JWT를 검증한 뒤 넣는 회원 ID입니다. 클라이언트가 보낸 같은 이름의 헤더는 Gateway가 지웁니다.
    static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final PolicySearchService policySearchService;

    @GetMapping("/search")
    public ApiResponse<PolicySearchResponseDTO> searchPolicies(
            @RequestHeader(value = MEMBER_ID_HEADER, required = false) Long memberId,
            @RequestParam("query") String query) {
        if (memberId == null) {
            throw new GeneralException(ErrorStatus.UNAUTHORIZED);
        }
        return ApiResponse.onSuccess(policySearchService.search(memberId, query));
    }
}
