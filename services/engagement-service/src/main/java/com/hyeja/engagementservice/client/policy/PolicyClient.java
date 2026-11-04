package com.hyeja.engagementservice.client.policy;

import com.hyeja.engagementservice.client.policy.dto.PolicyApiResponse;
import com.hyeja.engagementservice.client.policy.dto.PolicySnapshotDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "policy-service", url = "${clients.policy.url:}")
public interface PolicyClient {
    @GetMapping("/internal/policies/{policyId}")
    PolicyApiResponse<PolicySnapshotDTO> getPolicy(@PathVariable("policyId") String policyId);
}
