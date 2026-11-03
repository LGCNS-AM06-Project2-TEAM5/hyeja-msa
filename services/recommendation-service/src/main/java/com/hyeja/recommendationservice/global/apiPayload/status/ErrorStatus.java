package com.hyeja.recommendationservice.global.apiPayload.status;

import com.hyeja.recommendationservice.global.apiPayload.code.BaseErrorCode;
import com.hyeja.recommendationservice.global.apiPayload.dto.ReasonDTO;
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

    // recommendation-service가 쓰는 코드만 1차(hyeja-backend)에서 가져왔습니다.
    // 회원·조건 (F1 user-service 오류를 같은 코드·상태로 전달합니다. UserServiceErrorDecoder)
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "존재하지 않는 회원입니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_001", "등록된 조건이 없습니다."),

    // 정책 검색
    POLICY_SEARCH_NOT_HOUSING(HttpStatus.BAD_REQUEST, "POLICY_SEARCH_002", "혜자는 주거 관련 혜택을 알려드려요."),
    POLICY_SEARCH_QUERY_REQUIRED(HttpStatus.BAD_REQUEST, "POLICY_SEARCH_006", "검색어를 입력해 주세요."),
    POLICY_SEARCH_QUERY_TOO_LONG(HttpStatus.BAD_REQUEST, "POLICY_SEARCH_007", "검색어는 200자 이하여야 합니다.");

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
