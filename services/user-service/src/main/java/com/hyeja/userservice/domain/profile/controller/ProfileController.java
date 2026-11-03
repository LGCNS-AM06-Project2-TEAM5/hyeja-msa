package com.hyeja.userservice.domain.profile.controller;

import com.hyeja.userservice.domain.profile.dto.ProfileRequestDTO;
import com.hyeja.userservice.domain.profile.dto.ProfileResponseDTO;
import com.hyeja.userservice.domain.profile.service.ProfileService;
import com.hyeja.userservice.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// URL은 /api/members/me/profile이지만 다루는 데이터가 Profile이라 profile 도메인에 둡니다 (1차와 같음).
// memberId는 JwtAuthenticationFilter가 토큰에서 꺼낸 회원 ID입니다. 토큰이 없으면 SecurityConfig가 401로 막습니다.
@RestController
@RequestMapping("/api/members/me/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ApiResponse<ProfileResponseDTO> getMyProfile(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(profileService.getMyProfile(memberId));
    }

    // 조건 8개를 통째로 교체합니다. 선택 항목을 null로 보내면 기존 값이 지워집니다.
    @PatchMapping
    public ApiResponse<ProfileResponseDTO> updateMyProfile(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ProfileRequestDTO request
    ) {
        return ApiResponse.onSuccess(profileService.updateMyProfile(memberId, request));
    }
}
