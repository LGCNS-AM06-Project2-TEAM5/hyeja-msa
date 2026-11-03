package com.hyeja.recommendationservice.profile.client;

import com.hyeja.recommendationservice.global.apiPayload.ApiResponse;
import com.hyeja.recommendationservice.profile.dto.MemberProfileDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// F1 (recommendation → user). 주소는 Eureka 서비스 ID로 LoadBalancer가 풀고, Timeout은 application.yml의 default 설정,
// 오류는 user-service Client 설정의 UserServiceErrorDecoder가 처리합니다.
// 서비스 간 인증 헤더는 인프라 계약이 확정되면 이 메서드의 @RequestHeader 또는 user-service Client 설정의 RequestInterceptor로 추가합니다.
@FeignClient(name = "user-service")
public interface UserProfileFeignClient {

    @GetMapping("/internal/members/{memberId}/profile")
    ApiResponse<MemberProfileDTO> getMemberProfile(@PathVariable("memberId") Long memberId);
}
