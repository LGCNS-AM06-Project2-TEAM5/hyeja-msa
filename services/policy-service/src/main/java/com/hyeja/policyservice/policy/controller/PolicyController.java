package com.hyeja.policyservice.policy.controller;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping("/sync")
    public ApiResponse<String> syncPolicies(
            @RequestHeader(value = "X-Member-Id", required = false) String memberId,
            @RequestHeader(value = "X-Member-Role", required = false) String role) {
        // Gateway가 검증하고 재설정한 헤더만 사용합니다. JWT를 직접 해석하지 않습니다.
        requireAdmin(memberId, role);
        int savedCount = policyService.fetchAndSaveHousingPolicies();
        return ApiResponse.onSuccess(
                "온통청년 주거 정책 " + savedCount + "건 동기화가 완료되었습니다.");
    }

    private void requireAdmin(String memberId, String role) {
        try {
            if (memberId == null || Long.parseLong(memberId.trim()) <= 0) {
                throw new GeneralException(ErrorStatus.UNAUTHORIZED);
            }
        } catch (NumberFormatException exception) {
            throw new GeneralException(ErrorStatus.UNAUTHORIZED);
        }
        if (!"ADMIN".equals(role)) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }
    }
}
