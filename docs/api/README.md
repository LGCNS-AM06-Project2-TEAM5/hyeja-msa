# 서비스 간 계약

개발 전에 아래 형식을 먼저 합의합니다. 바뀌면 이 문서부터 고치고, 같은 PR에서 코드를 바꿉니다.
기준: [MSA 아키텍처 설계서](../architecture/HYEJA_MSA_Architecture_Design.md) + [설계서와 코드 대조 결과](../architecture/설계_코드_대조.md)

## 1. 외부 API (기존 29개, 경로·메서드·응답 유지)

| 서비스 | 경로 |
|---|---|
| user | `POST /api/members`, `POST /api/members/email-verifications`, `POST /api/members/email-verifications/confirmation`, `POST /api/members/login`, `POST /api/members/logout`, `GET /api/members/me`, `GET /api/members/find-email`, `PATCH /api/members/me/delete`, `GET·PATCH /api/members/me/profile` |
| policy | `GET /api/policies/housing`, `GET /api/policies/housing/me`, `GET /api/policies/{policyId}`, `POST /api/policies/sync`, `GET /api/policies/card-news/guest`, `GET /api/policies/card-news`, `GET /api/policies/card-detail/{policyId}`, `GET /api/regions`, `GET /api/terms`, `GET /api/terms/{termId}` |
| recommendation | `GET /api/policies/search` |
| engagement | `GET /api/favorite`, `POST·DELETE /api/favorite/{policyId}`, `GET /api/notification`, `PATCH /api/notification/{notificationId}/read`, `DELETE /api/notification/{notificationId}`, `POST /api/notification/admin/generate` |
| 공통 | `GET /api/health` (각 서비스) |

Gateway 라우팅과 인증 규칙은 [infra/gateway-service](../../infra/gateway-service/README.md)에 있습니다.

## 2. 내부 API (OpenFeign)

내부 경로는 `/internal/**`로 두고 Gateway에 노출하지 않습니다. 정확한 경로·필드는 제공 서비스 담당자가 확정합니다.

| # | 호출 → 제공 | 용도 | 지금 코드 위치 | 장애 시 | 상태 |
|---|---|---|---|---|---|
| F1 | recommendation → user | 회원의 내 조건 조회 | `PolicySearchService` → `ProfileService.getActiveProfile` | 검색 실패 응답 | 설계서 |
| F2 | recommendation → policy | 정책 후보 일괄 조회 (판정에 필요한 조건과 신청 지역 포함) | `PolicyRepository.searchActivePolicies`, `PolicyRegionRepository` | 검색 실패 응답 | 설계서 |
| F3 | recommendation → engagement | 관심 여부 일괄 조회 | `FavoriteRepository.findActivePolicyIds` | 하트 없이 응답 | 설계서 |
| F4 | policy → engagement | 관심 여부 일괄 조회 (목록·상세·카드뉴스) | `PolicyService`, `CardNewsService` | 하트 없이 응답 | 설계서 |
| F5 | engagement → policy | 관심 등록 시 정책 존재·스냅샷 확인 | `FavoriteService` → `PolicyRepository` | 등록 실패 안내 | 설계서 |
| F6 | policy → user | 회원의 내 조건 조회 (`/housing/me` 필터, 상세 판정, 회원 카드뉴스) | `PolicyService` 200·249행, `CardNewsService` 60행 → `ProfileService` | 비로그인 응답과 같게 | **코드 대조로 추가** |

두지 않는 호출 (#1 확정): 아래 두 경우는 Feign 계약을 만들지 않습니다.

- F7 (user → policy, 거주지 코드 검증·시군구 이름): user_db에 지역 읽기 복사본을 두고 policy와 같은 `region_sigungu.csv`로 기동 때 적재합니다.
- F8 (policy → recommendation, 정책 상세 자격 판정): 판정 코드(`PolicyEligibilityEvaluator` 등)를 policy와 recommendation이 같이 씁니다. 판정 규칙의 기준은 recommendation이 갖습니다.

규칙: 한 요청 안에서 A→B→A로 되돌아오는 호출은 만들지 않습니다. 여러 건은 반드시 일괄 조회로 한 번에 가져옵니다.

## 3. Kafka 이벤트

| 토픽 | 발행 | 구독 | 발생 시점 | 목적 | 범위 |
|---|---|---|---|---|---|
| `policy-updated` | policy | engagement | 수집·변경 저장 후 | 정책 스냅샷 읽기 모델 갱신 | **우선 구현** |
| `member-registered` | user | engagement | 가입 커밋 후 | 웰컴 메일 | 이번에 하지 않음 (#1 확정) |
| `notification-created` | engagement | engagement 내부 Worker | 앱 알림 저장 후 | 마감 알림 메일 발송 | 여유 있을 때 (#1 확정) |
| `member-withdrawn` | user | engagement | 탈퇴 커밋 후 | 관심 정책·알림 정리 | **구현 (#1 확정)** · 실패하면 재시도 후 DLT. Saga 보상은 이번 범위에 넣지 않고, 여유가 있으면 추가 기술로 검토 |

공통 필드: `eventId`(중복 처리 확인), `eventType`, `eventVersion`, `occurredAt`, `aggregateId`. 개인정보와 토큰은 넣지 않습니다.

`policy-updated` 본문(안): `policyId, policyName, categories, supportContent, applyEndDate, applyPeriodCode, applyUrl, activeYn, deleted`
— engagement의 관심 목록 응답과 키워드 검색(정책명·지원 내용)에 필요한 값입니다.

안전장치: 발행은 DB 커밋 뒤(Outbox 권장), 구독은 `eventId`로 중복 무시, 실패 시 재시도 후 DLT.
