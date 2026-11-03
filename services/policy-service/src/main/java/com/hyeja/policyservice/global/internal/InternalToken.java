package com.hyeja.policyservice.global.internal;

// 서비스끼리 내부 API를 부를 때 쓰는 헤더 이름입니다. 모든 서비스가 같은 이름과 같은 값(hyeja.internal.token)을 씁니다.
public final class InternalToken {

    public static final String HEADER = "X-Internal-Token";

    private InternalToken() {
    }
}
