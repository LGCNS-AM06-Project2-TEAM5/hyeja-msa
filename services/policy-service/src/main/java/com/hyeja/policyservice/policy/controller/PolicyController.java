package com.hyeja.policyservice.policy.controller;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import com.hyeja.policyservice.policy.dto.PolicyGuestResponseDTO;
import com.hyeja.policyservice.policy.dto.PolicyDetailResponseDTO;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicySort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/housing")
    public ApiResponse<PolicyGuestResponseDTO.PolicyListDTO> getGuestHousingPolicies(
            @RequestParam(name = "category", required = false) PolicyCategory category,
            @RequestParam(name = "sort", defaultValue = "DEADLINE") PolicySort sort,
            @RequestParam(name = "page", defaultValue = "0")
            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다.") int page,
            @RequestParam(name = "size", defaultValue = "8")
            @Positive(message = "페이지 크기는 양수여야 합니다.")
            @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.") int size) {
        return ApiResponse.onSuccess(policyService.getGuestHousingPolicies(category, sort, page, size));
    }

    @GetMapping("/{policyId}")
    public ApiResponse<PolicyDetailResponseDTO> getPolicyDetailForMember(
            @PathVariable("policyId") String policyId,
            @RequestHeader(value = "X-Member-Id", required = false) String memberId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        Long parsedMemberId = parseOptionalMemberId(memberId);
        // 무효 토큰의 검증은 Gateway 책임입니다. 인증 헤더만 남은 요청도 비회원으로 낮추지 않습니다.
        if (parsedMemberId == null && authorization != null) {
            throw new GeneralException(ErrorStatus.UNAUTHORIZED);
        }
        return ApiResponse.onSuccess(policyService.getPolicyDetailForMember(policyId, parsedMemberId));
    }

    private Long parseOptionalMemberId(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(memberId.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException exception) {
            throw new GeneralException(ErrorStatus.UNAUTHORIZED);
        }
        throw new GeneralException(ErrorStatus.UNAUTHORIZED);
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
