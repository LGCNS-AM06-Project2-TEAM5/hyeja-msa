# policy-service

청년 주거 정책의 수집·조회·맞춤 검색과 카드뉴스, 용어 풀이를 맡습니다.

| 항목 | 내용 |
|---|---|
| 경로 | `/api/policies/**`, `/api/terms/**` |
| 테이블 | policy, policy_region, card_news, term, region (DB: hyeja_policy) |
| 옮겨 올 코드 | [hyeja-backend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend)의 `domain/policy`, `domain/cardnews`, `domain/term`, `domain/region`(복사) |
| 외부 | 온통청년 정책 API |

- 호출(Feign): member-service(내 조건), favorite-service(하트 표시), ai-service(검색 의도·판정 이유)
- 발행(Kafka): `policy.collected`, `policy.updated`
- 구독(Kafka): `policy.analyzed` → 분석 결과 반영, 카드뉴스 생성
- card_news는 policy·policy_region과 함께 조회하므로 이 서비스에 둡니다.
- 바뀌는 점: 수집할 때 AI 분석을 기다리지 않고 원문만 저장한 뒤 이벤트를 발행합니다. `analysis_status`, `raw_hash` 컬럼을 추가합니다.
