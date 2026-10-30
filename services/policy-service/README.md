# policy-service

담당: 김예준, 홍준서 · 포트 8082 · DB `policy_db` (policy, policy_region, region, term, card_news)

정책 목록·상세·카테고리·정렬·페이징, 온통청년 정책 수집, AI 정책 분석과 카드뉴스, 지역, 용어 풀이를 맡습니다.

## 외부 API (Gateway 경로, 기존 계약 유지)

| 메서드 | 경로 | 인증 |
|---|---|---|
| GET | `/api/policies/housing` | 없음 |
| GET | `/api/policies/housing/me` | 로그인 |
| GET | `/api/policies/{policyId}` | 없음 (로그인 시 판정·하트 포함) |
| POST | `/api/policies/sync` | 관리자 |
| GET | `/api/policies/card-news/guest` | 없음 |
| GET | `/api/policies/card-news` | 로그인 |
| GET | `/api/policies/card-detail/{policyId}` | 없음 (로그인 시 하트 포함) |
| GET | `/api/regions` | 없음 |
| GET | `/api/terms`, `/api/terms/{termId}` | 없음 |

`/api/policies/search`는 recommendation-service로 갑니다.

## 옮겨 올 코드 (hyeja-backend)

- [`domain/policy`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/policy) 중 PolicyService·수집(PolicySyncItemService)·OpenAiPolicyAnalyzer (검색 관련 PolicySearchService·OpenAiPolicySearchAnalyzer는 recommendation으로)
- [`domain/cardnews`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/cardnews), [`domain/region`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/region), [`domain/term`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/term)

## 다른 서비스와의 연결

| 방향 | 방식 | 내용 |
|---|---|---|
| 제공 → recommendation | Feign | 정책 후보 일괄 조회 (판정에 필요한 나이·소득·혼인·취업·무주택 조건과 신청 지역 포함) |
| 제공 → engagement | Feign | 정책 존재·스냅샷 확인 (관심 등록 시) |
| 사용 → user | Feign | 내 조건 조회 — `/housing/me` 필터, 상세의 판정, 회원 카드뉴스 (설계서에 없던 호출, **추가 필요**) |
| 사용 → engagement | Feign | 관심 여부 일괄 조회 (목록·상세·카드뉴스의 하트) |
| 발행 | Kafka `policy-updated` | 수집·변경 후 정책 스냅샷 (engagement 읽기 모델 갱신) |

## 결정 필요

- 정책 상세(로그인)의 자격 판정(PolicyEligibilityEvaluator)을 policy에 둘지, recommendation을 호출할지. 지금 코드는 상세와 검색이 같은 판정 로직을 씁니다.
