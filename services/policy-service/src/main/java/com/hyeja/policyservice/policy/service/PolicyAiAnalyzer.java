package com.hyeja.policyservice.policy.service;

import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;

public interface PolicyAiAnalyzer {
    PolicyAiAnalysis analyze(PolicyItem item);
}

