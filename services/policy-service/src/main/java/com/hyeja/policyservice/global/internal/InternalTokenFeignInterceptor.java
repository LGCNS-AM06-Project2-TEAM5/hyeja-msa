package com.hyeja.policyservice.global.internal;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// policy가 부르는 모든 Feign 요청(user F6, engagement F4)에 X-Internal-Token을 붙입니다.
// 값이 비어 있으면 붙이지 않고, 받는 쪽이 401로 막으면 각 호출의 실패 처리(비로그인 응답 등)를 따릅니다.
@Component
public class InternalTokenFeignInterceptor implements RequestInterceptor {

    private final String token;

    public InternalTokenFeignInterceptor(@Value("${hyeja.internal.token:}") String token) {
        this.token = token;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (!token.isBlank()) {
            template.header(InternalToken.HEADER, token);
        }
    }
}
