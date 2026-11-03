package com.hyeja.policyservice.client.engagement.dto;

import java.util.List;

// F4 요청. engagement F3와 같은 API(POST /internal/favorites/active-policy-ids)를 쓰며 policyIds는 한 번에 20개까지입니다.
public record ActivePolicyIdsRequest(Long memberId, List<String> policyIds) {
}
