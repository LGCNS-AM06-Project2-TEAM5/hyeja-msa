package com.hyeja.policyservice.global.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Gateway가 JWT를 검증한 뒤 넘겨주는 X-Member-Id 헤더 값을 컨트롤러 파라미터로 받습니다.
// 1차의 @AuthenticationPrincipal Long memberId 자리에 씁니다.
// required = false이면 비로그인일 때 null이 들어옵니다(예: 정책 상세, 카드뉴스 상세).
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginMemberId {
    boolean required() default true;
}
