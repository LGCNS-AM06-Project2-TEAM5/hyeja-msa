package com.hyeja.policyservice.global.apiPayload.status;

import com.hyeja.policyservice.global.apiPayload.code.BaseErrorCode;
import com.hyeja.policyservice.global.apiPayload.dto.ReasonDTO;
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

    // policy-service가 쓰는 코드만 1차(hyeja-backend)에서 가져왔습니다. 회원·인증·관심 정책·검색 코드는 각 서비스가 가집니다.
    // 지역
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "REGION_001", "존재하지 않는 지역입니다."),

    // 용어
    TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "TERM_001", "존재하지 않는 용어입니다."),

    // 정책
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "POLICY_001", "존재하지 않는 정책입니다."),
    // 온통청년 API 요청이 재시도까지 실패해 수집이 멈춤. 그때까지 저장한 정책은 유지되고, result에 멈춘 페이지·저장 건수가 담깁니다.
    POLICY_SYNC_STOPPED(HttpStatus.BAD_GATEWAY, "POLICY_002", "온통청년 API 요청이 실패해 정책 수집이 중간에 멈췄어요. 잠시 후 다시 시도해 주세요."),
    CARD_NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "CARD_NEWS_001", "존재하지 않는 카드뉴스입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ReasonDTO getErrorReasonDTO() {
        return ReasonDTO.builder()
                .httpStatus(httpStatus).isSuccess(false)
                .code(code).message(message).build();
    }
}
