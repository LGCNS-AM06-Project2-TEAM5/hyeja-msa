# 서비스 간 계약

개발 전에 아래 형식을 먼저 합의합니다. 바뀌면 이 문서부터 고칩니다.

## 내부 API (OpenFeign, 초안)

| 호출 | 용도 | 실패 시 |
|---|---|---|
| policy → member | 회원의 내 조건 조회 | 비로그인 목록으로 대체 |
| policy → favorite | 회원의 관심 정책 ID 목록 | 하트 없이 목록 표시 |
| policy → ai | 검색 의도 분류, 판정 이유 | 기존 기본 동작 |
| favorite → policy | 정책 존재·정책명·마감일 확인 | 등록 실패 안내 |
| notification → favorite | 마감 D-7 대상 목록 | 다음 실행 때 재시도 |

## Kafka 이벤트 (초안)

| 토픽 | 발행 | 구독 | 내용 |
|---|---|---|---|
| `policy.collected` | policy | ai | 분석할 정책 원문 |
| `policy.analyzed` | ai | policy | 분석 결과(유형, 요약, 조건, 카드 문구) |
| `policy.updated` | policy | favorite | 정책명·마감일 변경 |
| `member.withdrawn` | member | favorite, notification | 탈퇴 회원 ID |
| `mail.requested` | member | notification | 받는 주소, 메일 종류, 인증 코드 |

모든 이벤트에 `eventId`(중복 처리 확인용)와 `occurredAt`을 넣습니다.
