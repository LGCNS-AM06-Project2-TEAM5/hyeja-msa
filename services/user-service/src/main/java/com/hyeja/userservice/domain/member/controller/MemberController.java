package com.hyeja.userservice.domain.member.controller;

import com.hyeja.userservice.domain.member.dto.MemberAccountResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberFindEmailResponseDTO;
import com.hyeja.userservice.domain.member.dto.MemberLoginRequestDTO;
import com.hyeja.userservice.domain.member.dto.MemberLoginResponseDTO;
import com.hyeja.userservice.domain.member.service.MemberService;
import com.hyeja.userservice.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/login")
    public ApiResponse<MemberLoginResponseDTO> login(@Valid @RequestBody MemberLoginRequestDTO request) {
        return ApiResponse.onSuccess(memberService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        memberService.logout((String) authentication.getCredentials());
        return ApiResponse.onSuccess(null);
    }

    @GetMapping("/me")
    public ApiResponse<MemberAccountResponseDTO> getMyAccount(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(memberService.getMyAccount(memberId));
    }

    @GetMapping("/find-email")
    public ApiResponse<MemberFindEmailResponseDTO> findEmail(
            @RequestParam String nickname,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birth
    ) {
        return ApiResponse.onSuccess(memberService.findEmail(nickname, birth));
    }
}
