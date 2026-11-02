package com.hyeja.userservice.domain.member.controller;

import com.hyeja.userservice.domain.member.dto.EmailVerificationRequestDTO;
import com.hyeja.userservice.domain.member.dto.EmailVerificationResponseDTO;
import com.hyeja.userservice.domain.member.service.EmailVerificationService;
import com.hyeja.userservice.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/email-verifications")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping
    public ApiResponse<EmailVerificationResponseDTO.SendDTO> send(
            @Valid @RequestBody EmailVerificationRequestDTO.SendDTO request
    ) {
        long expiresInSeconds = emailVerificationService.send(request.getEmail());
        return ApiResponse.onSuccess(new EmailVerificationResponseDTO.SendDTO(expiresInSeconds));
    }

    @PostMapping("/confirmation")
    public ApiResponse<EmailVerificationResponseDTO.ConfirmDTO> confirm(
            @Valid @RequestBody EmailVerificationRequestDTO.ConfirmDTO request
    ) {
        emailVerificationService.confirm(request.getEmail(), request.getCode());
        return ApiResponse.onSuccess(new EmailVerificationResponseDTO.ConfirmDTO(true));
    }
}
