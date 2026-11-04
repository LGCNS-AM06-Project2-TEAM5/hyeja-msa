# recommendation-service

담당: 류지범 · 포트 8084 · DB 없음

자연어·해시태그 맞춤 검색, 검색 의도 분석(AI), 내 조건과 정책 조건 비교(자격 판정), 판정 이유 생성(AI), 3그룹 분류(받을 수 있어요 / 확인이 필요해요 / 아쉽게 안 돼요)를 맡습니다.

## 외부 API (Gateway 경로, 기존 계약 유지)

| 메서드 | 경로 | 인증 |
|---|---|---|
| GET | `/api/policies/search?query=` | 로그인 |

요구사항 명세서의 `/api/recommendations`는 제안 경로였고, 프런트와 백엔드는 `/api/policies/search`를 씁니다. 경로는 그대로 두고 Gateway가 이 경로만 recommendation으로 보냅니다.

회원 ID는 Gateway가 JWT를 검증한 뒤 넣는 `X-Member-Id` 헤더로 받습니다(없으면 401 `COMMON_002`).

**현재 상태:** 검색 코어·AI 분석은 옮겼고, F1(user 회원 조건, `GET /internal/members/{memberId}/profile`)은 `UserProfileFeignClient`·`ProfileClientAdapter`·`UserServiceErrorDecoder`(404 `MEMBER_001`·`PROFILE_001` 그대로 전달), F2(policy 정책 후보, `POST /internal/policies/candidates`)는 `PolicyCandidateFeignClient`·`PolicyClientAdapter`, F3(engagement 관심 여부, `POST /internal/favorites/active-policy-ids`)는 `FavoriteFeignClient`·`FavoriteClientAdapter`로 구현했습니다. 검색 API는 `recommendation.search.enabled: true`(`application.yml` 기본값, PR #116)로 켜져 있고, 실행에는 `OPEN_AI_KEY`·`OPEN_AI_MODEL`이 필요합니다. engagement `SecurityConfig`가 `/internal/**`을 Gateway assertion 없이 허용하므로(PR #95) F3는 별도 인증 헤더 없이 호출하며, 실패하면 관심 표시 없이(`isFavorite=false`) 응답합니다. Gateway·프런트·실제 OpenAI를 거친 검색은 [Policy 최종 통합 검증](../../docs/05_테스트케이스/policy-final-integration-119.md)에서 확인했습니다.

**Circuit Breaker (Issue #103):** F1·F2·F3 Feign 호출은 각각 독립 회로 `recUserProfile`·`recPolicyCandidates`·`recEngagementFavorites`(Resilience4j, `application.yml`의 `resilience4j.circuitbreaker`)를 거칩니다. 최근 20회 중 10회 이상 기록되고 절반 이상 실패(5xx·연결 실패·Timeout·역직렬화 실패)하면 15초 동안 호출하지 않고, 이후 3회 시험 호출로 닫거나 다시 엽니다. 4xx와 `MEMBER_001`·`PROFILE_001`은 실패율에서 뺍니다. 회로가 열려 호출하지 않을 때도 기존과 같이 F1·F2는 검색 실패(500 `SERVER_001`), F3는 관심 표시 없이 응답합니다. Feign Timeout은 그대로이고 Retry는 두지 않습니다. 상태·호출 결과는 `/actuator/prometheus`의 `resilience4j_circuitbreaker_*` 메트릭과 상태 전환 WARN 로그로 확인합니다.

## 옮겨 올 코드 (hyeja-backend)

- [`domain/policy/service`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/policy/service) 중 PolicySearchService, PolicyEligibilityEvaluator, PolicyIncomeEligibilityEvaluator, OpenAiPolicySearchAnalyzer, PolicySearchAiAnalyzer
- 관련 DTO·enum (EligibilityStatus, EligibilityConditionType 등)

## 다른 서비스와의 연결

| 방향 | 방식 | 내용 |
|---|---|---|
| 사용 → user | Feign | 내 조건 조회 |
| 사용 → policy | Feign | 정책 후보 일괄 조회 (F2 `POST /internal/policies/candidates`) |
| 사용 → engagement | Feign | 관심 여부 일괄 조회 |
| 같은 코드 | 자격 판정 | 판정 코드(PolicyEligibilityEvaluator 등)를 policy에도 같은 내용으로 따로 둡니다(공유 모듈 아님). 판정 규칙의 기준은 recommendation이 갖습니다 (#1 확정) |
| 외부 | OpenAI | 의도 분류·판정 이유. 실패 시 기존 기본 동작 유지 (Circuit Breaker 대체 응답) |
