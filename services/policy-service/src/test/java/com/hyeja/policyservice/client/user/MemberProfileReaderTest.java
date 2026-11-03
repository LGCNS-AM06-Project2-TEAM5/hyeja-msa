package com.hyeja.policyservice.client.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.dto.UserApiResponse;
import com.hyeja.policyservice.client.user.enums.EmploymentStatus;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemberProfileReaderTest {

    private final UserClient userClient = mock(UserClient.class);
    private final MemberProfileReader reader = new MemberProfileReader(userClient);

    @Test
    void 회원_조건을_그대로_돌려준다() {
        MemberProfileDTO profile = new MemberProfileDTO(7L, LocalDate.of(2000, 3, 15),
                new MemberProfileDTO.RegionDTO("11440", "서울특별시 마포구"),
                EmploymentStatus.UNEMPLOYED, true, null);
        given(userClient.getMemberProfile(7L))
                .willReturn(new UserApiResponse<>(true, "SUCCESS_001", "성공입니다.", profile));

        assertThat(reader.getActiveProfile(7L)).isSameAs(profile);
    }

    @Test
    void 탈퇴하거나_없는_회원이면_MEMBER_001() {
        given(userClient.getMemberProfile(7L)).willThrow(errorResponse(404, "MEMBER_001"));

        assertError(ErrorStatus.MEMBER_NOT_FOUND);
    }

    @Test
    void 조건이_없으면_PROFILE_001() {
        given(userClient.getMemberProfile(7L)).willThrow(errorResponse(404, "PROFILE_001"));

        assertError(ErrorStatus.PROFILE_NOT_FOUND);
    }

    @Test
    void user_서비스가_500이면_응답_불가() {
        given(userClient.getMemberProfile(7L)).willThrow(errorResponse(500, "SERVER_001"));

        assertError(ErrorStatus.USER_SERVICE_UNAVAILABLE);
    }

    @Test
    void 코드를_알_수_없는_404도_응답_불가() {
        given(userClient.getMemberProfile(7L)).willThrow(errorResponse(404, "COMMON_005"));

        assertError(ErrorStatus.USER_SERVICE_UNAVAILABLE);
    }

    @Test
    void 연결이_안_되거나_시간이_지나면_응답_불가() {
        given(userClient.getMemberProfile(7L)).willThrow(new RetryableException(
                -1, "Read timed out", Request.HttpMethod.GET, (Long) null, request()));

        assertError(ErrorStatus.USER_SERVICE_UNAVAILABLE);
    }

    @Test
    void 성공_응답에_조건이_비어_있으면_응답_불가() {
        given(userClient.getMemberProfile(7L))
                .willReturn(new UserApiResponse<>(true, "SUCCESS_001", "성공입니다.", null));

        assertError(ErrorStatus.USER_SERVICE_UNAVAILABLE);
    }

    @Test
    void 실패_응답에_값이_들어_있어도_조건으로_쓰지_않는다() {
        MemberProfileDTO profile = new MemberProfileDTO(7L, null, null, null, null, null);
        given(userClient.getMemberProfile(7L))
                .willReturn(new UserApiResponse<>(false, "SERVER_001", "서버 오류가 발생했습니다.", profile));

        assertError(ErrorStatus.USER_SERVICE_UNAVAILABLE);
    }

    private void assertError(ErrorStatus expected) {
        assertThatThrownBy(() -> reader.getActiveProfile(7L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(expected);
    }

    private static FeignException errorResponse(int status, String code) {
        String body = "{\"isSuccess\":false,\"code\":\"" + code + "\",\"message\":\"x\",\"result\":null}";
        Response response = Response.builder()
                .status(status)
                .request(request())
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("UserClient#getMemberProfile(Long)", response);
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "/internal/members/7/profile",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }
}
