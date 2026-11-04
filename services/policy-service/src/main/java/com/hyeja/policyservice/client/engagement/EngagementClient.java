package com.hyeja.policyservice.client.engagement;

import com.hyeja.policyservice.client.engagement.dto.ActivePolicyIdsRequest;
import com.hyeja.policyservice.client.engagement.dto.EngagementApiResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// F4 (policy → engagement) 관심 여부 일괄 조회. recommendation F3와 같은 engagement 내부 API를 씁니다.
// 직접 쓰지 말고 FavoriteReader를 거칩니다(20개씩 나누기, 실패 시 하트 없이 응답).
@FeignClient(name = "engagement-service", contextId = "engagementClient", path = "/internal/favorites")
public interface EngagementClient {

    @PostMapping("/active-policy-ids")
    EngagementApiResponse<List<String>> findActivePolicyIds(@RequestBody ActivePolicyIdsRequest request);
}
