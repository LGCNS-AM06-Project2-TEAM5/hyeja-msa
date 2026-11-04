package com.hyeja.policyservice.client.user;

import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.dto.UserApiResponse;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// user-service 응답을 policy 오류 코드로 바꿉니다.
// 탈퇴·없는 회원(MEMBER_001), 조건 없음(PROFILE_001)은 1차 ProfileService.getActiveProfile과 같은 코드로 그대로 돌려주고,
// 그 밖의 실패(연결 실패, 시간 초과, 5xx)와 policyUserProfile 회로가 열려 호출하지 않은 경우는 USER_SERVICE_UNAVAILABLE로 바꿉니다.
// 비로그인 응답으로 대신할지는 부르는 쪽이 정합니다(상세는 대신하고, /housing/me·회원 카드뉴스는 실패 안내).
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberProfileReader {

    static final String CIRCUIT_BREAKER = "policyUserProfile";
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_0-9]+)\"");

    private final UserClient userClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public MemberProfileDTO getActiveProfile(Long memberId) {
        UserApiResponse<MemberProfileDTO> response;
        try {
            response = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER)
                    .executeSupplier(() -> userClient.getMemberProfile(memberId));
        } catch (FeignException e) {
            throw translate(e);
        } catch (CallNotPermittedException e) {
            log.warn("user-service 회로가 열려 있어 회원 조건을 조회하지 않았습니다.");
            throw new GeneralException(ErrorStatus.USER_SERVICE_UNAVAILABLE);
        }
        if (response == null || !Boolean.TRUE.equals(response.isSuccess()) || response.result() == null) {
            log.warn("user-service 회원 조건 응답이 비어 있습니다.");
            throw new GeneralException(ErrorStatus.USER_SERVICE_UNAVAILABLE);
        }
        return response.result();
    }

    /** Maps known downstream errors without logging Feign messages that may contain response bodies. */
    private GeneralException translate(FeignException e) {
        if (e.status() == 404) {
            String code = errorCode(e.contentUTF8());
            if (ErrorStatus.MEMBER_NOT_FOUND.getCode().equals(code)) {
                return new GeneralException(ErrorStatus.MEMBER_NOT_FOUND);
            }
            if (ErrorStatus.PROFILE_NOT_FOUND.getCode().equals(code)) {
                return new GeneralException(ErrorStatus.PROFILE_NOT_FOUND);
            }
        }
        log.warn("user-service 회원 조건 조회 실패. status={}, errorType={}",
                e.status(), e.getClass().getSimpleName());
        return new GeneralException(ErrorStatus.USER_SERVICE_UNAVAILABLE);
    }

    private static String errorCode(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = ERROR_CODE.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
