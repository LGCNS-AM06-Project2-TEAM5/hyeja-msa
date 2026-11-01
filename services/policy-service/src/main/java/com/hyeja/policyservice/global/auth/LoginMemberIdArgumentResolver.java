package com.hyeja.policyservice.global.auth;

import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class LoginMemberIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMemberId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String value = webRequest.getHeader(MemberHeaders.MEMBER_ID);
        if (value == null || value.isBlank()) {
            if (parameter.getParameterAnnotation(LoginMemberId.class).required()) {
                throw new GeneralException(ErrorStatus.UNAUTHORIZED);
            }
            return null;
        }
        // Gateway만 이 헤더를 붙이므로 숫자가 아니면 잘못 연결된 요청으로 보고 401을 돌려줍니다.
        try {
            long memberId = Long.parseLong(value.trim());
            if (memberId <= 0) {
                throw new GeneralException(ErrorStatus.UNAUTHORIZED);
            }
            return memberId;
        } catch (NumberFormatException e) {
            throw new GeneralException(ErrorStatus.UNAUTHORIZED);
        }
    }
}
