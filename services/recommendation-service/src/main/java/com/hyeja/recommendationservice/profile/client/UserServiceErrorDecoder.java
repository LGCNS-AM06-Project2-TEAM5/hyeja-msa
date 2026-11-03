package com.hyeja.recommendationservice.profile.client;

import com.hyeja.recommendationservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.recommendationservice.global.exception.GeneralException;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// F1(user-service) 오류 중 MEMBER_001·PROFILE_001만 같은 코드·상태의 GeneralException으로 바꿔 검색 응답에 그대로 전달합니다.
// 나머지 오류는 기본 FeignException으로 두어 검색 실패(SERVER_001)가 되게 하며, 빈 조건으로 바꾸지 않습니다.
// application.yml의 spring.cloud.openfeign.client.config.user-service.error-decoder로 등록합니다.
public class UserServiceErrorDecoder implements ErrorDecoder {
    private static final List<ErrorStatus> PASS_THROUGH =
            List.of(ErrorStatus.MEMBER_NOT_FOUND, ErrorStatus.PROFILE_NOT_FOUND);
    private static final JsonMapper JSON = new JsonMapper();

    private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        byte[] body = readBody(response);
        String code = errorCode(body);
        for (ErrorStatus status : PASS_THROUGH) {
            if (status.getHttpStatus().value() == response.status() && status.getCode().equals(code)) {
                return new GeneralException(status);
            }
        }
        // 본문을 이미 읽었으므로 다시 넣어 FeignException에 원래 본문이 남게 합니다.
        return defaultDecoder.decode(methodKey,
                body == null ? response : response.toBuilder().body(body).build());
    }

    // user-service 오류 본문은 ApiResponse 실패 형식({"isSuccess":false,"code":...})입니다.
    // 본문이 없거나 JSON이 아니거나 code가 문자열이 아니면 null을 돌려 장애(FeignException)로 전파합니다.
    protected String errorCode(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode code = JSON.readTree(body).get("code");
            return code != null && code.isString() ? code.stringValue() : null;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private static byte[] readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            return Util.toByteArray(in);
        } catch (IOException exception) {
            return null;
        }
    }
}
