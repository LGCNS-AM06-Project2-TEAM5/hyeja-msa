package com.hyeja.userservice.domain.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

public final class EmailVerificationResponseDTO {

    private EmailVerificationResponseDTO() {
    }

    @Getter
    @AllArgsConstructor
    public static class SendDTO {

        private long expiresInSeconds;
    }

    @Getter
    @AllArgsConstructor
    public static class ConfirmDTO {

        private boolean verified;
    }

    @Getter
    @AllArgsConstructor
    public static class MismatchDTO {

        private long remainingAttempts;
    }
}
