package com.hyeja.policyservice.event.outbox;

import com.hyeja.policyservice.global.apiPayload.ApiResponse;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.auth.LoginMemberId;
import com.hyeja.policyservice.global.auth.MemberHeaders;
import com.hyeja.policyservice.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 화면의 이벤트 동기화 카드가 부르는 API입니다.
// /api/policies/** 경로라 Gateway 수정 없이 policy로 오고, 두 단계 경로라 Gateway에서 로그인이 필요합니다.
@RestController
@RequestMapping("/api/policies/admin")
@RequiredArgsConstructor
public class PolicyOutboxAdminController {

    private static final String ADMIN_ROLE = "ADMIN";

    private final PolicyOutboxStatusService statusService;

    @GetMapping("/events")
    public ApiResponse<PolicyOutboxStatusDTO> getEventStatus(
            @LoginMemberId Long memberId,
            @RequestHeader(value = MemberHeaders.MEMBER_ROLE, required = false) String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }
        return ApiResponse.onSuccess(statusService.getStatus());
    }
}
