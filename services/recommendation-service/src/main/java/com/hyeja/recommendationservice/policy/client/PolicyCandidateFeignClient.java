package com.hyeja.recommendationservice.policy.client;

import com.hyeja.recommendationservice.global.apiPayload.ApiResponse;
import com.hyeja.recommendationservice.policy.dto.PolicyCandidateDTO;
import com.hyeja.recommendationservice.policy.dto.PolicyCandidateRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// F2 (recommendation → policy). 주소는 Eureka 서비스 ID로 LoadBalancer가 풀고, Timeout은 application.yml의 default 설정을 씁니다.
// 서비스 간 인증 헤더는 인프라 계약이 확정되면 이 메서드의 @RequestHeader 또는 policy-service Client 설정의 RequestInterceptor로 추가합니다.
@FeignClient(name = "policy-service")
public interface PolicyCandidateFeignClient {

    @PostMapping("/internal/policies/candidates")
    ApiResponse<List<PolicyCandidateDTO>> findCandidates(@RequestBody PolicyCandidateRequest request);
}
