# ai-service

OpenAI 호출을 한곳에 모은 서비스입니다. DB는 없습니다.

| 항목 | 내용 |
|---|---|
| 경로 | 내부 API만 (Gateway에 노출하지 않음) |
| 옮겨 올 코드 | [hyeja-backend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend)의 `OpenAiPolicyAnalyzer`, `OpenAiPolicySearchAnalyzer`, `global/config/OpenAiConfig` |
| 외부 | OpenAI API |

- 제공(Feign): 검색 의도 분류, 판정 이유 문장
- 구독(Kafka): `policy.collected` → 정책 분석
- 발행(Kafka): `policy.analyzed`
- 장애 대비: 타임아웃, Circuit Breaker. 실패 시 호출한 쪽이 기존 기본 동작으로 대체합니다.
