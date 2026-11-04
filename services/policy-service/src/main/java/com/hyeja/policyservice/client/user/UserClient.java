package com.hyeja.policyservice.client.user;

import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.dto.UserApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// F6 (policy → user) 회원 조건 조회. 계약은 docs/api/README.md "F1·F6 회원 조건 조회"입니다.
// 직접 쓰지 말고 MemberProfileReader를 거칩니다(오류 코드 변환).
@FeignClient(name = "user-service", contextId = "userClient", path = "/internal/members")
public interface UserClient {

    @GetMapping("/{memberId}/profile")
    UserApiResponse<MemberProfileDTO> getMemberProfile(@PathVariable("memberId") Long memberId);
}
