package com.hyeja.recommendationservice.policy.service;

import com.hyeja.recommendationservice.policy.dto.ConditionResultDTO;
import com.hyeja.recommendationservice.policy.enums.EligibilityStatus;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PolicySearchAiAnalyzer {
    SearchIntent analyzeIntent(String query);

    Map<String, AiAssessment> assess(List<ReasonRequest> requests);

    record SearchIntent(boolean housingRelated, Set<PolicyCategory> categories) {
    }

    record ReasonRequest(
            String policyId,
            String policyName,
            String extraQualification,
            ProfileSummary profile,
            EligibilityStatus overallStatus,
            List<ConditionResultDTO> conditions) {
    }

    record ProfileSummary(
            String birth,
            String region,
            String employment,
            String houseless,
            String maritalStatus,
            String income,
            String education,
            String housingType) {
    }

    record AiAssessment(EligibilityStatus status, String reason) {
    }

    enum FailureType {
        UNAVAILABLE,
        EMPTY_RESPONSE,
        INVALID_RESPONSE
    }

    final class AnalysisException extends RuntimeException {
        private final FailureType failureType;

        public AnalysisException(FailureType failureType, String message) {
            super(message);
            this.failureType = failureType;
        }

        public AnalysisException(FailureType failureType, String message, Throwable cause) {
            super(message, cause);
            this.failureType = failureType;
        }

        public FailureType getFailureType() {
            return failureType;
        }
    }
}
