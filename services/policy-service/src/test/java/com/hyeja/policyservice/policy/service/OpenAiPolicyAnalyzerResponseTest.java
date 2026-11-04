package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OpenAiPolicyAnalyzerResponseTest {
    private static final String VALID_JSON = """
            {
              "description": " 월세를 지원합니다. ",
              "eligibilityDescription": " 무주택 청년이 대상입니다. ",
              "benefitTitle": " 월세 지원 ",
              "benefitDescription": " 월 임차료를 지원합니다. ",
              "applicationDescription": " 온라인으로 신청합니다. ",
              "categories": ["OTHER", "MONTHLY_RENT"],
              "categoryConfidence": 0.9, "categoryReason": "월세 정책",
              "houselessRequirement": "REQUIRED",
              "houselessConfidence": 0.8, "houselessReason": "무주택 명시",
              "incomeCondition": "COMPARABLE", "incomeMin": 100, "incomeMax": 300,
              "incomeConfidence": 0.7, "incomeReason": "개인 연소득"
            }
            """;

    private final OpenAIClient client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
    private final OpenAiPolicyAnalyzer analyzer =
            new OpenAiPolicyAnalyzer(client, new ObjectMapper());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(analyzer, "model", "test-model");
    }

    @Test
    void sendsStructuredRequestAndParsesAllAnalysisFields() {
        respondsWith(VALID_JSON);

        PolicyAiAnalysis analysis = analyzer.analyze(item());

        assertThat(analysis.description()).isEqualTo("월세를 지원합니다.");
        assertThat(analysis.categories()).containsExactly(PolicyCategory.MONTHLY_RENT);
        assertThat(analysis.categoryConfidence()).isEqualTo(0.9);
        assertThat(analysis.categoryReason()).isEqualTo("월세 정책");
        assertThat(analysis.houselessRequirement()).isEqualTo(PolicyHouselessRequirement.REQUIRED);
        assertThat(analysis.houselessConfidence()).isEqualTo(0.8);
        assertThat(analysis.houselessReason()).isEqualTo("무주택 명시");
        assertThat(analysis.incomeCondition()).isEqualTo(PolicyIncomeCondition.COMPARABLE);
        assertThat(analysis.incomeMin()).isEqualTo(100);
        assertThat(analysis.incomeMax()).isEqualTo(300);
        assertThat(analysis.incomeConfidence()).isEqualTo(0.7);
        assertThat(analysis.incomeReason()).isEqualTo("개인 연소득");
        assertThat(analysis.eligibilityDescription()).isEqualTo("무주택 청년이 대상입니다.");
        assertThat(analysis.benefitTitle()).isEqualTo("월세 지원");
        assertThat(analysis.benefitDescription()).isEqualTo("월 임차료를 지원합니다.");
        assertThat(analysis.applicationDescription()).isEqualTo("온라인으로 신청합니다.");

        ArgumentCaptor<ResponseCreateParams> request = ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(client.responses()).create(request.capture());
        assertThat(request.getValue().model().orElseThrow().asString()).isEqualTo("test-model");
        var format = request.getValue().text().orElseThrow().format().orElseThrow().asJsonSchema();
        assertThat(format.name()).isEqualTo("policy_analysis");
        assertThat(format.strict()).contains(true);
        assertThat(format.schema()._additionalProperties()).containsKey("additionalProperties");
    }

    @Test
    void changesComparableWithoutAmountsToUnknown() {
        respondsWith(VALID_JSON.replace("\"incomeMin\": 100, \"incomeMax\": 300",
                "\"incomeMin\": null, \"incomeMax\": null"));
        PolicyAiAnalysis result = analyzer.analyze(item());
        assertThat(result.incomeCondition()).isEqualTo(PolicyIncomeCondition.UNKNOWN);
        assertThat(result.incomeMin()).isNull();
        assertThat(result.incomeMax()).isNull();
    }

    @Test
    void removesAmountsForNonComparableIncome() {
        respondsWith(VALID_JSON.replace("\"COMPARABLE\"", "\"CONDITIONAL\""));
        PolicyAiAnalysis result = analyzer.analyze(item());
        assertThat(result.incomeCondition()).isEqualTo(PolicyIncomeCondition.CONDITIONAL);
        assertThat(result.incomeMin()).isNull();
        assertThat(result.incomeMax()).isNull();
    }

    @Test
    void rejectsMalformedJsonWithOriginalFailureMessage() {
        respondsWith("not-json");
        assertThatThrownBy(() -> analyzer.analyze(item()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI 정책 분석 응답을 해석할 수 없습니다.");
    }

    @Test
    void rejectsEmptyResponseWithOriginalFailureMessage() {
        Response response = mock(Response.class);
        when(response.output()).thenReturn(List.of());
        when(client.responses().create(any(ResponseCreateParams.class))).thenReturn(response);
        assertThatThrownBy(() -> analyzer.analyze(item()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenAI 정책 분석 응답이 비어 있습니다.");
    }

    @Test
    void propagatesApiFailureWithoutInventingAnalysis() {
        IllegalStateException failure = new IllegalStateException("API unavailable");
        when(client.responses().create(any(ResponseCreateParams.class))).thenThrow(failure);
        assertThatThrownBy(() -> analyzer.analyze(item())).isSameAs(failure);
    }

    private void respondsWith(String json) {
        var message = ResponseOutputMessage.builder()
                .id("message-test")
                .status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder().text(json).annotations(List.of()).build())
                .build();
        Response response = mock(Response.class);
        when(response.output()).thenReturn(List.of(ResponseOutputItem.ofMessage(message)));
        when(client.responses().create(any(ResponseCreateParams.class))).thenReturn(response);
    }

    private PolicyItem item() {
        PolicyItem item = new PolicyItem();
        item.setPolicyId("policy-test");
        item.setPolicyName("청년 월세 지원");
        return item;
    }
}
