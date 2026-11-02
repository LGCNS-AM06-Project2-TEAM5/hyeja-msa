package com.hyeja.userservice.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public final class EmailVerificationRequestDTO {

    private EmailVerificationRequestDTO() {
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SendDTO {

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 아니에요.")
        private String email;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConfirmDTO {

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 아니에요.")
        private String email;

        @NotBlank(message = "인증 코드를 입력해 주세요.")
        @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 숫자 6자리예요.")
        private String code;
    }
}
