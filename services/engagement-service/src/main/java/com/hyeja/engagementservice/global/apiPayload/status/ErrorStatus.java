package com.hyeja.engagementservice.global.apiPayload.status;

import com.hyeja.engagementservice.global.apiPayload.code.BaseErrorCode;
import com.hyeja.engagementservice.global.apiPayload.dto.ReasonDTO;
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

    // 회원
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "존재하지 않는 회원입니다."),
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "MEMBER_002", "이미 가입된 이메일입니다."),
    MEMBER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "MEMBER_003", "이미 사용 중인 닉네임입니다."),
    // 이메일 찾기 실패: 닉네임·생년월일 중 무엇이 틀렸는지 구분하지 않습니다(가입된 닉네임 노출 방지).
    MEMBER_EMAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_004", "가입된 정보가 없어요."),
    // 로그인 실패: 이메일·비밀번호 중 무엇이 틀렸는지, 탈퇴 회원인지 구분하지 않습니다(가입된 이메일 노출 방지).
    MEMBER_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "MEMBER_005", "이메일 또는 비밀번호가 올바르지 않아요."),
    // 탈퇴 시 비밀번호 확인 실패: 401이면 프론트가 "로그인 풀림"으로 처리하므로 400입니다.
    MEMBER_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "MEMBER_006", "비밀번호가 올바르지 않아요."),

    // 회원가입 이메일 인증
    VERIFY_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "VERIFY_001", "인증 코드가 일치하지 않아요."),
    VERIFY_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "VERIFY_002", "인증 코드가 만료됐어요. 다시 받아 주세요."),
    VERIFY_REQUIRED(HttpStatus.BAD_REQUEST, "VERIFY_003", "이메일 인증을 먼저 완료해 주세요."),
    VERIFY_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "VERIFY_004", "잠시 후에 다시 요청해 주세요."),
    VERIFY_TOO_MANY_FAILURES(HttpStatus.TOO_MANY_REQUESTS, "VERIFY_005", "인증 시도 횟수를 초과했어요. 1시간 후에 다시 시도해 주세요."),
    MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MAIL_001", "메일 발송에 실패했어요. 잠시 후 다시 시도해 주세요."),

    // 알림
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_001", "존재하지 않는 알림입니다."),

    // 프로필
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "PROFILE_001", "등록된 조건이 없습니다."),

    // 지역
    REGION_NOT_FOUND(HttpStatus.NOT_FOUND, "REGION_001", "존재하지 않는 지역입니다."),

    // 용어
    TERM_NOT_FOUND(HttpStatus.NOT_FOUND, "TERM_001", "존재하지 않는 용어입니다."),

    // 정책
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "POLICY_001", "존재하지 않는 정책입니다."),
    // 온통청년 API 요청이 재시도까지 실패해 수집이 멈춤. 그때까지 저장한 정책은 유지되고, result에 멈춘 페이지·저장 건수가 담깁니다.
    POLICY_SYNC_STOPPED(HttpStatus.BAD_GATEWAY, "POLICY_002", "온통청년 API 요청이 실패해 정책 수집이 중간에 멈췄어요. 잠시 후 다시 시도해 주세요."),
    POLICY_SEARCH_EMPTY(HttpStatus.OK, "POLICY_SEARCH_001", "조건에 맞는 정책을 찾지 못했어요."),
    POLICY_SEARCH_NOT_HOUSING(HttpStatus.BAD_REQUEST, "POLICY_SEARCH_002", "혜자는 주거 관련 혜택을 알려드려요."),
    POLICY_SEARCH_AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "POLICY_SEARCH_003", "AI 검색 서비스에 연결할 수 없어요. 잠시 후 다시 시도해 주세요."),
    POLICY_SEARCH_AI_EMPTY_RESPONSE(HttpStatus.BAD_GATEWAY, "POLICY_SEARCH_004", "AI 검색 서비스에서 결과를 받지 못했어요. 다시 시도해 주세요."),
    POLICY_SEARCH_AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "POLICY_SEARCH_005", "AI 검색 결과를 해석할 수 없어요. 다시 시도해 주세요."),
    POLICY_SEARCH_QUERY_REQUIRED(HttpStatus.BAD_REQUEST, "POLICY_SEARCH_006", "검색어를 입력해 주세요."),
    POLICY_SEARCH_QUERY_TOO_LONG(HttpStatus.BAD_REQUEST, "POLICY_SEARCH_007", "검색어는 200자 이하여야 합니다."),
    CARD_NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "CARD_NEWS_001", "존재하지 않는 카드뉴스입니다."),

    // 관심 정책
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "FAVORITE_001", "이미 등록된 관심 정책입니다."),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "FAVORITE_002", "등록되지 않은 관심 정책입니다.");

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
