package com.hyeja.policyservice.policy.controller;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.global.auth.LoginMemberId;
import com.hyeja.policyservice.policy.dto.PolicyMemberResponseDTO.PolicyListDTO;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicySort;
import com.hyeja.policyservice.policy.service.PolicyMemberService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 로그인 회원 전용 정책 API. 비로그인 목록·상세(PolicyController)와 파일을 나눠 둡니다(11-01 분담).
// 토큰이 없으면 Gateway가 먼저 401로 막습니다(/api/policies/housing/me는 인증 필요).
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyMemberController {

    private final PolicyMemberService policyMemberService;

    // 내 조건에 맞는 주거 정책 목록. 파라미터는 1차와 같고, onlyEligible이면 회원 조건으로 거릅니다.
    @GetMapping("/housing/me")
    public ApiResponse<PolicyListDTO> getHousingPoliciesForMember(
            @LoginMemberId Long memberId,
            @RequestParam(name = "category", required = false) PolicyCategory category,
            @RequestParam(name = "sort", defaultValue = "DEADLINE") PolicySort sort,
            @RequestParam(name = "onlyEligible", defaultValue = "false") boolean onlyEligible,
            @RequestParam(name = "page", defaultValue = "0")
            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다.") int page,
            @RequestParam(name = "size", defaultValue = "8")
            @Positive(message = "페이지 크기는 양수여야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.") int size) {
        return ApiResponse.onSuccess(policyMemberService.getHousingPoliciesForMember(
                memberId, category, sort, onlyEligible, page, size));
    }
}
