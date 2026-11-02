package com.hyeja.policyservice.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyeja.policyservice.policy.dto.PolicyApiResponseDTO.PolicyItem;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseTextConfig;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiPolicyAnalyzer implements PolicyAiAnalyzer {
    private static final String SYSTEM_PROMPT = """
            당신은 대한민국 청년 주거정책의 정보를 구조화하는 분석기다.
            제공된 정책 내용에 명시된 사실만 사용하고 조건을 추측하지 않는다.

            [정책 요약]
            description은 사용자가 정책의 핵심 지원 내용과 대상을 빠르게 이해할 수 있도록
            정책명, 지원 내용, 참여 대상을 근거로 한국어 1~2문장으로 요약한다.
            원문에 없는 혜택이나 자격조건을 추가하지 않는다.
            모든 문장은 반드시 격식 있는 존댓말로 작성하고,
            '~합니다', '~됩니다', '~할 수 있습니다'와 같은 형태로 끝낸다.
            '~한다', '~이다', '~함', '~지원'과 같은 서술체·명사형 종결은 사용하지 않는다.
            제목, 불릿, 번호 없이 자연스러운 문장만 반환한다.

            [카드뉴스]
            eligibilityDescription은 신청 대상과 핵심 자격조건을 쉬운 존댓말 1~2문장으로 작성한다.
            benefitTitle은 원문에 명시된 내용만 바탕으로 지원 혜택을 짧은 제목으로 작성한다.
            benefitDescription은 지원 내용과 지급 방식을 쉬운 존댓말 1~2문장으로 작성한다.
            applicationDescription은 신청 방법과 준비 사항을 쉬운 존댓말 1~2문장으로 작성한다.
            원문에 없는 조건, 금액, 기간, 신청 방법은 만들지 않는다.

            [카테고리]
            categories는 다음 값 중 하나 이상을 배열로 반환한다.
            - MONTHLY_RENT: 월세, 월 임차료 또는 월세 보증금 지원
            - JEONSE: 전세자금, 전세보증금, 전세대출 또는 전세보증 지원
            - PURCHASE: 주택 구입, 청약, 분양 또는 구입자금 지원
            - PUBLIC_RENT: 공공임대주택, 행복주택, 청년주택, 매입임대 또는 전세임대 입주
            - OTHER: 기숙사, 이사비, 중개비, 상담, 주거환경 개선 또는 명확히 분류되지 않음
            정책이 여러 유형을 실제로 함께 지원하면 해당 유형을 모두 선택한다.
            OTHER는 다른 네 유형으로 분류할 근거가 없을 때만 단독으로 선택한다.

            [무주택 조건]
            houselessRequirement는 반드시 다음 값 중 하나여야 한다.
            - REQUIRED: 신청자, 대상자, 세대주 또는 세대구성원이 무주택자여야 함
            - NOT_REQUIRED: 무주택 여부와 관계없이 신청 가능함이 명시됨
            - UNKNOWN: 무주택 관련 정보가 없거나 조건이 모호함
            '무주택자', '무주택 세대구성원', '무주택 세대주', '주택을 소유하지 않은 자'는
            REQUIRED의 근거가 될 수 있다.
            단순히 주거정책이거나 임대주택이라는 이유로 REQUIRED로 판단하지 않는다.
            무주택 문구를 찾지 못했다면 NOT_REQUIRED가 아니라 UNKNOWN으로 판단한다.

            [소득 조건]
            incomeCondition은 반드시 다음 값 중 하나여야 한다.
            - NO_RESTRICTION: 소득 제한이 없다고 명시됨
            - COMPARABLE: 개인 연소득의 최소·최대 금액으로 회원 프로필과 직접 비교 가능함
            - CONDITIONAL: 중위소득 비율, 소득·자산 점수, 부모·부부·가구 합산,
              고용·혼인 상태에 따라 기준이 달라지는 등 현재 프로필로 단순 비교할 수 없음
            - UNKNOWN: 소득 정보가 없거나 해석할 수 없음
            COMPARABLE일 때만 개인 연소득 기준을 incomeMin과 incomeMax에 원 단위 정수로 반환한다.
            하한이 없으면 incomeMin은 null, 상한이 없으면 incomeMax는 null이다.
            COMPARABLE이 아니면 incomeMin과 incomeMax를 모두 null로 반환한다.
            근거가 없을 때 NO_RESTRICTION으로 추측하지 않는다.

            각 confidence는 0.0에서 1.0 사이의 숫자로 반환한다.
            각 reason은 판단 근거를 한국어 한 문장으로 반환한다.
            """;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.ofEntries(
                    Map.entry("description", Map.of("type", "string", "minLength", 1)),
                    Map.entry("eligibilityDescription", Map.of("type", "string", "minLength", 1)),
                    Map.entry("benefitTitle", Map.of("type", "string", "minLength", 1)),
                    Map.entry("benefitDescription", Map.of("type", "string", "minLength", 1)),
                    Map.entry("applicationDescription", Map.of("type", "string", "minLength", 1)),
                    Map.entry("categories", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string", "enum", List.of(
                                    "MONTHLY_RENT", "JEONSE", "PURCHASE", "PUBLIC_RENT", "OTHER")),
                            "minItems", 1)),
                    Map.entry("categoryConfidence", Map.of("type", "number", "minimum", 0, "maximum", 1)),
                    Map.entry("categoryReason", Map.of("type", "string")),
                    Map.entry("houselessRequirement", Map.of(
                            "type", "string",
                            "enum", List.of("REQUIRED", "NOT_REQUIRED", "UNKNOWN"))),
                    Map.entry("houselessConfidence", Map.of("type", "number", "minimum", 0, "maximum", 1)),
                    Map.entry("houselessReason", Map.of("type", "string")),
                    Map.entry("incomeCondition", Map.of(
                            "type", "string",
                            "enum", List.of("NO_RESTRICTION", "COMPARABLE", "CONDITIONAL", "UNKNOWN"))),
                    Map.entry("incomeMin", Map.of("type", List.of("integer", "null"))),
                    Map.entry("incomeMax", Map.of("type", List.of("integer", "null"))),
                    Map.entry("incomeConfidence", Map.of("type", "number", "minimum", 0, "maximum", 1)),
                    Map.entry("incomeReason", Map.of("type", "string"))),
            "required", List.of(
                    "description", "eligibilityDescription", "benefitTitle",
                    "benefitDescription", "applicationDescription",
                    "categories", "categoryConfidence", "categoryReason",
                    "houselessRequirement", "houselessConfidence", "houselessReason",
                    "incomeCondition", "incomeMin", "incomeMax",
                    "incomeConfidence", "incomeReason"),
            "additionalProperties", false);

    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    @Override
    public PolicyAiAnalysis analyze(PolicyItem item) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .inputOfResponse(List.of(
                        message(EasyInputMessage.Role.SYSTEM, SYSTEM_PROMPT),
                        message(EasyInputMessage.Role.USER, buildUserPrompt(item))))
                .text(ResponseTextConfig.builder()
                        .format(ResponseFormatTextJsonSchemaConfig.builder()
                                .name("policy_analysis")
                                .strict(true)
                                .schema(JsonValue.from(RESPONSE_SCHEMA)
                                        .convert(ResponseFormatTextJsonSchemaConfig.Schema.class))
                                .build())
                        .build())
                .build();

        Response response = openAIClient.responses().create(params);
        String responseText = response.output().stream()
                .flatMap(output -> output.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OpenAI 정책 분석 응답이 비어 있습니다."));

        AiPolicyResponse result = parseResponse(responseText);
        PolicyIncomeCondition incomeCondition = normalizeIncomeCondition(result);
        PolicyAiAnalysis analysis = new PolicyAiAnalysis(
                result.description().trim(),
                normalizeCategories(result.categories()),
                result.categoryConfidence(),
                result.categoryReason(),
                toHouselessRequirement(result.houselessRequirement()),
                result.houselessConfidence(),
                result.houselessReason(),
                incomeCondition,
                normalizedIncomeMin(result, incomeCondition),
                normalizedIncomeMax(result, incomeCondition),
                result.incomeConfidence(),
                result.incomeReason(),
                result.eligibilityDescription().trim(),
                result.benefitTitle().trim(),
                result.benefitDescription().trim(),
                result.applicationDescription().trim());

        log.info("AI 정책 카테고리 분석 - policyId={}, categories={}, confidence={}, reason={}",
                item.getPolicyId(), analysis.categories(),
                analysis.categoryConfidence(), analysis.categoryReason());
        log.info("AI 정책 무주택 조건 분석 - policyId={}, requirement={}, confidence={}, reason={}",
                item.getPolicyId(), analysis.houselessRequirement(),
                analysis.houselessConfidence(), analysis.houselessReason());
        log.info("AI 정책 소득 조건 분석 - policyId={}, condition={}, min={}, max={}, confidence={}, reason={}",
                item.getPolicyId(), analysis.incomeCondition(), analysis.incomeMin(),
                analysis.incomeMax(), analysis.incomeConfidence(), analysis.incomeReason());
        return analysis;
    }

    private ResponseInputItem message(EasyInputMessage.Role role, String content) {
        return ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                .role(role)
                .content(content)
                .build());
    }

    String buildUserPrompt(PolicyItem item) {
        return """
                다음 정책의 주거정책 하위 유형과 무주택 자격조건을 분석하라.

                정책명: %s
                API 중분류: %s
                주거 유형: %s
                키워드: %s
                정책 설명: %s
                지원 내용: %s
                추가 자격조건: %s
                참여 대상: %s
                신청 방법: %s
                API 소득 조건 코드: %s
                API 소득 최솟값: %s
                API 소득 최댓값: %s
                소득 상세 조건: %s
                """.formatted(
                valueOrEmpty(item.getPolicyName()),
                valueOrEmpty(item.getSubCategory()),
                valueOrEmpty(item.getSubCategory()),
                valueOrEmpty(item.getKeywords()),
                valueOrEmpty(item.getPolicyExplanation()),
                valueOrEmpty(item.getSupportContent()),
                valueOrEmpty(item.getExtraQualification()),
                valueOrEmpty(item.getParticipantTarget()),
                valueOrEmpty(item.getApplyMethod()),
                valueOrEmpty(item.getIncomeConditionCode()),
                valueOrEmpty(item.getIncomeMin()),
                valueOrEmpty(item.getIncomeMax()),
                valueOrEmpty(item.getIncomeEtc()));
    }

    private AiPolicyResponse parseResponse(String responseText) {
        try {
            return objectMapper.readValue(responseText, AiPolicyResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OpenAI 정책 분석 응답을 해석할 수 없습니다.", exception);
        }
    }

    PolicyHouselessRequirement toHouselessRequirement(String requirement) {
        return PolicyHouselessRequirement.valueOf(requirement);
    }

    PolicyIncomeCondition normalizeIncomeCondition(AiPolicyResponse result) {
        PolicyIncomeCondition condition = PolicyIncomeCondition.valueOf(result.incomeCondition());
        if (condition == PolicyIncomeCondition.COMPARABLE
                && result.incomeMin() == null && result.incomeMax() == null) {
            log.warn("AI가 COMPARABLE을 반환했지만 소득 금액이 없어 UNKNOWN으로 보정합니다.");
            return PolicyIncomeCondition.UNKNOWN;
        }
        return condition;
    }

    Set<PolicyCategory> normalizeCategories(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of(PolicyCategory.OTHER);
        }
        Set<PolicyCategory> categories = values.stream()
                .map(PolicyCategory::valueOf)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (categories.size() > 1) {
            categories.remove(PolicyCategory.OTHER);
        }
        return Set.copyOf(categories);
    }

    private Integer normalizedIncomeMin(
            AiPolicyResponse result, PolicyIncomeCondition condition) {
        return condition == PolicyIncomeCondition.COMPARABLE
                ? result.incomeMin() : null;
    }

    private Integer normalizedIncomeMax(
            AiPolicyResponse result, PolicyIncomeCondition condition) {
        return condition == PolicyIncomeCondition.COMPARABLE
                ? result.incomeMax() : null;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record AiPolicyResponse(
            String description,
            String eligibilityDescription,
            String benefitTitle,
            String benefitDescription,
            String applicationDescription,
            List<String> categories,
            double categoryConfidence,
            String categoryReason,
            String houselessRequirement,
            double houselessConfidence,
            String houselessReason,
            String incomeCondition,
            Integer incomeMin,
            Integer incomeMax,
            double incomeConfidence,
            String incomeReason) {
    }
}

