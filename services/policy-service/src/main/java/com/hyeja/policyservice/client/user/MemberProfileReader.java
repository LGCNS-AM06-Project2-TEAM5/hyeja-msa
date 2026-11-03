package com.hyeja.policyservice.client.user;

import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.dto.UserApiResponse;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import feign.FeignException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// user-service 응답을 policy 오류 코드로 바꿉니다.
// 탈퇴·없는 회원(MEMBER_001), 조건 없음(PROFILE_001)은 1차 ProfileService.getActiveProfile과 같은 코드로 그대로 돌려주고,
// 그 밖의 실패(연결 실패, 시간 초과, 5xx)는 USER_SERVICE_UNAVAILABLE로 바꿉니다.
// 비로그인 응답으로 대신할지는 부르는 쪽이 정합니다(상세는 대신하고, /housing/me·회원 카드뉴스는 실패 안내).
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberProfileReader {

    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_0-9]+)\"");

    private final UserClient userClient;

    public MemberProfileDTO getActiveProfile(Long memberId) {
        UserApiResponse<MemberProfileDTO> response;
        try {
            response = userClient.getMemberProfile(memberId);
        } catch (FeignException e) {
            throw translate(e, memberId);
        }
        if (response == null || !Boolean.TRUE.equals(response.isSuccess()) || response.result() == null) {
            log.warn("user-service 회원 조건 응답이 비어 있습니다. memberId={}", memberId);
            throw new GeneralException(ErrorStatus.USER_SERVICE_UNAVAILABLE);
        }
        return response.result();
    }

    private GeneralException translate(FeignException e, Long memberId) {
        if (e.status() == 404) {
            String code = errorCode(e.contentUTF8());
            if (ErrorStatus.MEMBER_NOT_FOUND.getCode().equals(code)) {
                return new GeneralException(ErrorStatus.MEMBER_NOT_FOUND);
            }
            if (ErrorStatus.PROFILE_NOT_FOUND.getCode().equals(code)) {
                return new GeneralException(ErrorStatus.PROFILE_NOT_FOUND);
            }
        }
        log.warn("user-service 회원 조건 조회 실패. memberId={}, status={}, message={}",
                memberId, e.status(), e.getMessage());
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
