package com.hyeja.policyservice.global.auth;

// Gateway(infra/gateway-service)가 검증된 회원 정보를 담아 보내는 헤더 이름입니다.
// 외부에서 보낸 같은 이름의 헤더는 Gateway가 지우므로 이 서비스는 JWT를 직접 풀지 않습니다.
public final class MemberHeaders {

    public static final String MEMBER_ID = "X-Member-Id";
    public static final String MEMBER_ROLE = "X-Member-Role";

    private MemberHeaders() {
    }
}
