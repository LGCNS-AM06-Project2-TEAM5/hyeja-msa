# recommendation-service

담당: 류지범 · 포트 8084 · DB 없음

자연어·해시태그 맞춤 검색, 검색 의도 분석(AI), 내 조건과 정책 조건 비교(자격 판정), 판정 이유 생성(AI), 3그룹 분류(받을 수 있어요 / 확인이 필요해요 / 아쉽게 안 돼요)를 맡습니다.

## 외부 API (Gateway 경로, 기존 계약 유지)

| 메서드 | 경로 | 인증 |
|---|---|---|
| GET | `/api/policies/search?query=` | 로그인 |

요구사항 명세서의 `/api/recommendations`는 제안 경로였고, 프런트와 백엔드는 `/api/policies/search`를 씁니다. 경로는 그대로 두고 Gateway가 이 경로만 recommendation으로 보냅니다.

## 옮겨 올 코드 (hyeja-backend)

- [`domain/policy/service`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/policy/service) 중 PolicySearchService, PolicyEligibilityEvaluator, PolicyIncomeEligibilityEvaluator, OpenAiPolicySearchAnalyzer, PolicySearchAiAnalyzer
- 관련 DTO·enum (EligibilityStatus, EligibilityConditionType 등)

## 다른 서비스와의 연결

| 방향 | 방식 | 내용 |
|---|---|---|
| 사용 → user | Feign | 내 조건 조회 |
| 사용 → policy | Feign | 정책 후보 일괄 조회 (지금 코드는 PolicyRepository.searchActivePolicies 직접 호출) |
| 사용 → engagement | Feign | 관심 여부 일괄 조회 |
| 공유 코드 | 자격 판정 | 판정 코드(PolicyEligibilityEvaluator 등)를 policy의 정책 상세와 같이 씁니다. 판정 규칙의 기준은 recommendation이 갖습니다 (#1 확정) |
| 외부 | OpenAI | 의도 분류·판정 이유. 실패 시 기존 기본 동작 유지 (Circuit Breaker 대체 응답) |
