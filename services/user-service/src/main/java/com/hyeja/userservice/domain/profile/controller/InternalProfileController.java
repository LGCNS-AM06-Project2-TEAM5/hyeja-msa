package com.hyeja.userservice.domain.profile.controller;

import com.hyeja.userservice.domain.profile.dto.MemberProfileDTO;
import com.hyeja.userservice.domain.profile.service.ProfileService;
import com.hyeja.userservice.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 서비스 간 호출(Feign) 전용입니다. Gateway는 /api/members/**만 라우팅하므로 외부에 노출되지 않습니다.
@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class InternalProfileController {

    private final ProfileService profileService;

    @GetMapping("/{memberId}/profile")
    public ApiResponse<MemberProfileDTO> getMemberProfile(@PathVariable Long memberId) {
        return ApiResponse.onSuccess(profileService.getMemberProfile(memberId));
    }
}
