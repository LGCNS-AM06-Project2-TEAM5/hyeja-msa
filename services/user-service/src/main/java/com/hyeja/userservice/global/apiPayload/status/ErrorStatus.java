package com.hyeja.userservice.global.apiPayload.status;

import com.hyeja.userservice.global.apiPayload.code.BaseErrorCode;
import com.hyeja.userservice.global.apiPayload.dto.ReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_002", "인증이 필요합니다."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "COMMON_003", "입력값을 확인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_004", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_005", "요청한 대상을 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_006", "지원하지 않는 요청 메서드입니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_007", "요청이 현재 상태와 충돌합니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_008", "지원하지 않는 요청 형식입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "COMMON_009", "요청한 응답 형식을 제공할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_001", "서버 오류가 발생했습니다."),

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "존재하지 않는 회원입니다."),
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "MEMBER_002", "이미 가입된 이메일입니다."),
    MEMBER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "MEMBER_003", "이미 사용 중인 닉네임입니다."),
    MEMBER_EMAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_004", "가입된 정보가 없어요."),
    MEMBER_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "MEMBER_005", "이메일 또는 비밀번호가 올바르지 않아요."),
    MEMBER_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "MEMBER_006", "비밀번호가 올바르지 않아요."),

    VERIFY_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "VERIFY_001", "인증 코드가 일치하지 않아요."),
    VERIFY_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "VERIFY_002", "인증 코드가 만료됐어요. 다시 받아 주세요."),
    VERIFY_REQUIRED(HttpStatus.BAD_REQUEST, "VERIFY_003", "이메일 인증을 먼저 완료해 주세요."),
    VERIFY_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "VERIFY_004", "잠시 후에 다시 요청해 주세요."),
    VERIFY_TOO_MANY_FAILURES(HttpStatus.TOO_MANY_REQUESTS, "VERIFY_005", "인증 시도 횟수를 초과했어요. 1시간 후에 다시 시도해 주세요."),
    MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MAIL_001", "메일 발송에 실패했어요. 잠시 후 다시 시도해 주세요."),

    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_001", "등록된 조건이 없습니다."),
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "REGION_001", "존재하지 않는 지역입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ReasonDTO getErrorReasonDTO() {
        return ReasonDTO.builder()
                .httpStatus(httpStatus)
                .isSuccess(false)
                .code(code)
                .message(message)
                .build();
    }
}
