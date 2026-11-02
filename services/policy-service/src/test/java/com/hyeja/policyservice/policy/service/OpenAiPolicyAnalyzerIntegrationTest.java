package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hyeja.policyservice.global.config.OpenAiConfig;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("openai-integration")
class OpenAiPolicyAnalyzerIntegrationTest {

    @Test
    void analyzesSamplePolicyUsingRealOpenAi() {
        String apiKey = System.getenv("OPEN_AI_KEY");
        String model = System.getenv("OPEN_AI_MODEL");
        assertTrue(apiKey != null && !apiKey.isBlank(), "OPEN_AI_KEY 환경변수가 필요합니다.");
        assertTrue(model != null && !model.isBlank(), "OPEN_AI_MODEL 환경변수가 필요합니다.");

        OpenAiConfig config = new OpenAiConfig();
        OpenAIClient client = config.openAIClient(apiKey);
        try {
            OpenAiPolicyAnalyzer analyzer = new OpenAiPolicyAnalyzer(client, config.objectMapper());
            ReflectionTestUtils.setField(analyzer, "model", model);
            PolicyItem item = new PolicyItem();
            item.setPolicyId("integration-sample");
            item.setPolicyName("테스트용 청년 월세 지원");
            item.setSubCategory("월세 지원");
            item.setSupportContent("개인 연소득 3천만원 이하 무주택 청년에게 월 임차료를 지원합니다.");
            item.setExtraQualification("만 19세 이상 34세 이하 무주택자");
            item.setParticipantTarget("청년");
            item.setApplyMethod("온라인 신청");

            PolicyAiAnalysis result = analyzer.analyze(item);

            assertThat(result.description()).isNotBlank();
            assertThat(result.categories()).isNotEmpty();
            assertThat(result.houselessRequirement()).isNotNull();
            assertThat(result.incomeCondition()).isNotNull();
            assertThat(result.eligibilityDescription()).isNotBlank();
            assertThat(result.benefitTitle()).isNotBlank();
            assertThat(result.benefitDescription()).isNotBlank();
            assertThat(result.applicationDescription()).isNotBlank();
        } finally {
            client.close();
        }
    }
}
