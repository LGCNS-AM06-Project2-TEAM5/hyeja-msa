package com.hyeja.recommendationservice.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyeja.recommendationservice.policy.dto.ConditionResultDTO;
import com.hyeja.recommendationservice.policy.enums.EligibilityStatus;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseTextConfig;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 1차 OpenAiPolicySearchAnalyzer와 같은 프롬프트·JSON Schema·응답 해석 규칙입니다.
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recommendation.search.enabled", havingValue = "true")
public class OpenAiPolicySearchAnalyzer implements PolicySearchAiAnalyzer {
    private static final String INTENT_PROMPT = """
            사용자의 질문이 청년 주거 정책 검색인지 판별하고 관련 카테고리를 고르세요.
            카테고리는 MONTHLY_RENT, JEONSE, PURCHASE, PUBLIC_RENT, OTHER 중 하나 이상입니다.
            월세는 MONTHLY_RENT, 전세는 JEONSE, 청약·주택 구입은 PURCHASE,
            공공임대는 PUBLIC_RENT이며 그 밖의 주거 지원은 OTHER입니다.
            취업·창업·교육처럼 주거와 무관한 질문은 housingRelated를 false로 반환하세요.
            명시되지 않은 의도를 추측하지 마세요.
            """;
    private static final String REASON_PROMPT = """
            청년 주거 정책 검색 결과에 표시할 개인화 이유를 작성하세요.
            서버의 overallStatus와 evaluation, profile, extraQualification을 함께 검토해 최종 status와 reason을 작성하세요.
            서버의 status보다 유리하게 바꾸면 안 됩니다. ABLE은 UNKNOWN 또는 DISABLE로, UNKNOWN은 DISABLE로만 변경할 수 있고,
            DISABLE은 반드시 DISABLE로 유지하세요. 확실히 판단할 근거가 부족하면 UNKNOWN을 선택하세요.
            제공된 profile과 extraQualification을 직접 비교한 내용도 이유에 반영하세요.
            추가 자격에 필요한 profile 정보가 없거나 문장만으로 판단할 수 없으면 확인이 필요하다고 안내하세요.
            제공된 회원 정보와 정책 조건만 사용하고 없는 사실은 추측하지 마세요.
            reason에 정책명은 쓰지 말고 각 policyId의 정보만 사용하세요.
            ABLE에는 구조화 조건과 추가 자격이 회원 정보에 어떻게 부합하는지만 설명하세요.
            UNKNOWN에는 profile과 extraQualification을 비교해도 확정할 수 없는 조건만 설명하세요.
            DISABLE에는 구조화 조건 또는 추가 자격 중 회원 정보와 맞지 않는 조건만 설명하세요.
            상태를 선언하는 접두 문구 없이 사유만 한 문장으로 작성하고, 반드시 자연스러운 해요체인 "요."로 끝내세요.
            UNKNOWN과 DISABLE에는 신청 가능성이나 모든 조건을 충족했다는 표현을 절대 쓰지 마세요.
            """;
    private static final Map<String, Object> INTENT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "housingRelated", Map.of("type", "boolean"),
                    "categories", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string", "enum", List.of(
                                    "MONTHLY_RENT", "JEONSE", "PURCHASE", "PUBLIC_RENT", "OTHER")))),
            "required", List.of("housingRelated", "categories"),
            "additionalProperties", false);
    private static final Map<String, Object> REASON_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("reasons", Map.of(
                    "type", "array",
                    "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "policyId", Map.of("type", "string"),
                                    "status", Map.of("type", "string", "enum", List.of(
                                            "ABLE", "UNKNOWN", "DISABLE")),
                                    "reason", Map.of("type", "string", "minLength", 1)),
                            "required", List.of("policyId", "status", "reason"),
                            "additionalProperties", false))),
            "required", List.of("reasons"),
            "additionalProperties", false);

    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    @Override
    public SearchIntent analyzeIntent(String query) {
        IntentResponse response = parse(call(INTENT_PROMPT, query, "policy_search_intent", INTENT_SCHEMA),
                IntentResponse.class);
        Set<PolicyCategory> categories;
        try {
            categories = response.categories() == null
                    ? Set.of()
                    : response.categories().stream()
                            .map(PolicyCategory::valueOf)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (IllegalArgumentException exception) {
            throw new AnalysisException(FailureType.INVALID_RESPONSE,
                    "OpenAI 검색 분석 응답에 알 수 없는 카테고리가 있습니다.", exception);
        }
        return new SearchIntent(response.housingRelated(), Set.copyOf(categories));
    }

    @Override
    public Map<String, AiAssessment> assess(List<ReasonRequest> requests) {
        String input = requests.stream().map(this::reasonInput)
                .collect(java.util.stream.Collectors.joining("\n\n"));
        ReasonsResponse response = parse(call(REASON_PROMPT, input, "policy_search_reasons", REASON_SCHEMA),
                ReasonsResponse.class);
        Map<String, AiAssessment> reasons = new LinkedHashMap<>();
        if (response.reasons() != null) {
            response.reasons().forEach(item -> {
                if (item.policyId() != null && item.status() != null
                        && item.reason() != null && !item.reason().isBlank()) {
                    reasons.putIfAbsent(item.policyId(),
                            new AiAssessment(item.status(), item.reason().trim()));
                }
            });
        }
        return reasons;
    }

    private String reasonInput(ReasonRequest request) {
        String conditions = request.conditions().stream()
                .map(this::conditionLine)
                .collect(java.util.stream.Collectors.joining("\n"));
        return """
                policyId: %s
                policyName: %s
                extraQualification: %s
                profile: %s
                overallStatus: %s
                evaluation:
                %s
                """.formatted(
                request.policyId(), request.policyName(), request.extraQualification(),
                request.profile(), request.overallStatus(), conditions);
    }

    private String conditionLine(ConditionResultDTO condition) {
        return "- %s: status=%s, policyCondition=%s, memberValue=%s".formatted(
                condition.type(), condition.status(), condition.policyCondition(), condition.memberValue());
    }

    private String call(String systemPrompt, String userPrompt, String schemaName,
            Map<String, Object> schema) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .inputOfResponse(List.of(
                        message(EasyInputMessage.Role.SYSTEM, systemPrompt),
                        message(EasyInputMessage.Role.USER, userPrompt)))
                .text(ResponseTextConfig.builder()
                        .format(ResponseFormatTextJsonSchemaConfig.builder()
                                .name(schemaName)
                                .strict(true)
                                .schema(JsonValue.from(schema)
                                        .convert(ResponseFormatTextJsonSchemaConfig.Schema.class))
                                .build())
                        .build())
                .build();
        Response response;
        try {
            response = openAIClient.responses().create(params);
        } catch (RuntimeException exception) {
            throw new AnalysisException(FailureType.UNAVAILABLE,
                    "OpenAI 검색 분석 서비스를 호출할 수 없습니다.", exception);
        }
        return response.output().stream()
                .flatMap(output -> output.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(output -> output.text())
                .findFirst()
                .orElseThrow(() -> new AnalysisException(FailureType.EMPTY_RESPONSE,
                        "OpenAI 검색 분석 응답이 비어 있습니다."));
    }

    private ResponseInputItem message(EasyInputMessage.Role role, String content) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role).content(content).build());
    }

    private <T> T parse(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new AnalysisException(FailureType.INVALID_RESPONSE,
                    "OpenAI 검색 분석 응답을 해석할 수 없습니다.", exception);
        }
    }

    private record IntentResponse(boolean housingRelated, List<String> categories) {
    }

    private record ReasonsResponse(List<ReasonItem> reasons) {
    }

    private record ReasonItem(String policyId, EligibilityStatus status, String reason) {
    }
}
