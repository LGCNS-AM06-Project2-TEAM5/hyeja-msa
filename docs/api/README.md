# 서비스 간 계약

현재 구현된 관심·알림 외부 API와 미구현 연동 범위는 [Engagement API 명세서](engagement-api.md)를 참고합니다.

개발 전에 아래 형식을 먼저 합의합니다. 바뀌면 이 문서부터 고치고, 같은 PR에서 코드를 바꿉니다.
기준: [MSA 아키텍처 설계서](../architecture/HYEJA_MSA_Architecture_Design.md) + [설계서와 코드 대조 결과](../architecture/설계_코드_대조.md)

## 1. 외부 API (기존 계약 기준, 알림 수동 생성 제외)

| 서비스 | 경로 |
|---|---|
| user | `POST /api/members`, `POST /api/members/email-verifications`, `POST /api/members/email-verifications/confirmation`, `POST /api/members/login`, `POST /api/members/logout`, `GET /api/members/me`, `GET /api/members/find-email`, `PATCH /api/members/me/delete`, `GET·PATCH /api/members/me/profile` |
| policy | `GET /api/policies/housing`, `GET /api/policies/housing/me`, `GET /api/policies/{policyId}`, `POST /api/policies/sync`, `GET /api/policies/card-news/guest`, `GET /api/policies/card-news`, `GET /api/policies/card-detail/{policyId}`, `GET /api/regions`, `GET /api/terms`, `GET /api/terms/{termId}` |
| recommendation | `GET /api/policies/search` |
| engagement | `GET /api/favorite`, `POST·DELETE /api/favorite/{policyId}`, `GET /api/notification`, `PATCH /api/notification/{notificationId}/read`, `DELETE /api/notification/{notificationId}` |
| 공통 | `GET /api/health` (각 서비스) |

Gateway 라우팅과 인증 규칙은 [infra/gateway-service](../../infra/gateway-service/README.md)에 있습니다.

## 2. 내부 API (OpenFeign)

내부 경로는 `/internal/**`로 두고 Gateway에 노출하지 않습니다. 정확한 경로·필드는 제공 서비스 담당자가 확정합니다.

| # | 호출 → 제공 | 용도 | 지금 코드 위치 | 장애 시 | 상태 |
|---|---|---|---|---|---|
| F1 | recommendation → user | 회원의 내 조건 조회 | `PolicySearchService` → `ProfileService.getActiveProfile` | 검색 실패 응답 | 확정 (user) |
| F2 | recommendation → policy | 정책 후보 일괄 조회 (판정에 필요한 조건과 신청 지역 포함) | `PolicyCandidateController` → `PolicyCandidateService` → `PolicyRepository.searchActivePolicies` | 검색 실패 응답 | 계약 합의·구현 완료 (아래 F2 계약) |
| F3 | recommendation → engagement | 관심 여부 일괄 조회 | `FavoriteRepository.findActivePolicyIds` | 하트 없이 응답 | 설계서 |
| F4 | policy → engagement | 관심 여부 일괄 조회 (목록·상세·카드뉴스) | `PolicyService`, `CardNewsService` | 하트 없이 응답 | 설계서 |
| F5 | engagement → policy | 관심 등록 시 정책 존재·스냅샷 확인 | `PolicySnapshotController` → `PolicySnapshotService` → `PolicyRepository.findById` | 등록 실패 안내 | 계약 합의·이번 변경에서 구현 (아래 F5 계약) |
| F6 | policy → user | 회원의 내 조건 조회 (`/housing/me` 필터, 상세 판정, 회원 카드뉴스) | `PolicyService` 200·249행, `CardNewsService` 60행 → `ProfileService` | 비로그인 응답과 같게 | 확정 (user) |

두지 않는 호출 (#1 확정): 아래 두 경우는 Feign 계약을 만들지 않습니다.

- F7 (user → policy, 거주지 코드 검증·시군구 이름): user_db에 지역 읽기 복사본을 두고 policy와 같은 `region_sigungu.csv`로 기동 때 적재합니다.
- F8 (policy → recommendation, 정책 상세 자격 판정): 판정 코드(`PolicyEligibilityEvaluator` 등)를 policy와 recommendation이 같이 씁니다. 판정 규칙의 기준은 recommendation이 갖습니다.

규칙: 한 요청 안에서 A→B→A로 되돌아오는 호출은 만들지 않습니다. 여러 건은 반드시 일괄 조회로 한 번에 가져옵니다.

### F1·F6 회원 조건 조회 (user 제공)

`GET /internal/members/{memberId}/profile`

200 OK: `ApiResponse<MemberProfileDTO>`

```json
{
  "isSuccess": true,
  "code": "SUCCESS_001",
  "message": "성공입니다.",
  "result": {
    "memberId": 1,
    "birth": "2000-03-15",
    "region": { "regionCode": "11440", "sigunguName": "서울특별시 마포구" },
    "employmentCode": "EMPLOYED",
    "houselessYn": true,
    "marriageCode": "SINGLE",
    "incomeRangeCode": "R2000_3000",
    "educationCode": null,
    "housingType": "MONTHLY_RENT"
  }
}
```

- `marriageCode`, `incomeRangeCode`, `educationCode`, `housingType`은 선택 항목이라 `null`일 수 있습니다.
- 필드명과 enum 값은 1차 코드(hyeja-backend)의 Profile과 같습니다.
- 나이는 계산하지 않고 `birth`만 반환합니다. 판정 기준 날짜는 호출하는 쪽이 정합니다.
- `sigunguName`은 user_db의 지역 읽기 복사본에서 채웁니다. 복사본에 해당 코드가 없으면(행정구역 변경 등) `null`이며, 이때도 `regionCode`는 채워집니다.

오류 (ApiResponse 실패 형식, 1차 `ProfileService.getActiveProfile`과 같은 기준)

| 상태 | 코드 | 경우 |
|---|---|---|
| 404 | `MEMBER_001` | 회원이 없거나 탈퇴함 |
| 404 | `PROFILE_001` | 조건 미등록 또는 삭제됨 (프론트는 온보딩으로 이동) |
| 400 | `COMMON_001` | `memberId` 형식 오류 |
| 500 | `SERVER_001` | 내부 처리 오류 |

인증: user-service `SecurityConfig`에서 `/internal/**`을 JWT 없이 허용합니다. Gateway가 라우팅하지 않아 외부에는 노출되지 않습니다. 서비스 간 인증 수단은 인프라 담당자와 정한 뒤 추가합니다.

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


## F2 정책 후보 조회 (Issue #53)

Recommendation 담당자와 요청·응답·조회 규칙·오류 계약 합의 완료(2026-11-02).
원본 `Hyejabonjuui/backend`의 `3e47bb1` `PolicySearchService` 및 `searchActivePolicies` 기준입니다.

- 호출: recommendation → policy, `POST /internal/policies/candidates`
- Content-Type: `application/json`
- 요청: `PolicyCandidateRequest`

```json
{
  "categories": ["MONTHLY_RENT", "JEONSE"],
  "regionCode": "11440",
  "today": "2026-11-02"
}
```

| 필드 | 타입 | 규칙 |
|---|---|---|
| categories | PolicyCategory 배열 | 필수·null 원소 불가, 빈 배열은 전체. MONTHLY_RENT / JEONSE / PURCHASE / PUBLIC_RENT / OTHER |
| regionCode | 문자열 | 필수·숫자 5자리 시군구 코드. 지역 테이블 존재 확인은 하지 않음 |
| today | ISO 날짜 문자열 | 필수. Recommendation이 한국 시간 기준 판정 날짜와 동일하게 전달 |

회원 ID·검색 문장·정렬·limit는 요청하지 않습니다. 정렬과 최대 20건은 Policy가 고정합니다.

응답: `ApiResponse<List<PolicyCandidateDTO>>`, 성공은 `200 / SUCCESS_001`.
후보가 없으면 `result: []`이며 장애를 빈 목록으로 바꾸지 않습니다.

| 정책별 응답 필드 | 타입 |
|---|---|
| policyId, policyName | 문자열 |
| categories | PolicyCategory 배열 |
| applyPeriodCode | SPECIFIC_PERIOD / ALWAYS / CLOSED |
| applyStartDate, applyEndDate | ISO 날짜 또는 null |
| ageLimitYn | Boolean |
| minAge, maxAge | 정수 또는 null |
| incomeConditionCode | NO_RESTRICTION / COMPARABLE / CONDITIONAL / UNKNOWN 또는 null |
| incomeMin, incomeMax | 정수 또는 null |
| incomeEtc | 문자열 또는 null |
| employmentCodes | 취업 조건 enum 배열 또는 null |
| houselessRequirement | REQUIRED / NOT_REQUIRED / UNKNOWN |
| extraQualification | 문자열 또는 null |
| regions | `[{"regionCode":"11440","sigunguName":"서울특별시 마포구"}]` |

employmentCodes 값: EMPLOYED / SELF_EMPLOYED / UNEMPLOYED / FREELANCER / DAILY_WORKER /
ENTREPRENEUR / SHORT_TERM_WORKER / FARMER / OTHER / NO_RESTRICTION.
원본 저장 값의 null·enum을 유지합니다. 배열 순서를 판정에 사용하지 않습니다.
regions는 매칭 지역뿐 아니라 정책의 전체 활성 대상 지역(코드순)을 반환하며 빈 배열은 전국입니다.
삭제된 정책-지역 연결과 삭제된 지역은 조회에서 제외합니다.

조회 규칙:

- 삭제·비활성·CLOSED·신청 시작 전·마감일 경과 정책 제외. 시작일·마감일 당일 포함, null 날짜는 제한 없음.
- 요청 카테고리는 OR. 전국·동일 시군구 또는 기존 `xx000` 시도 코드가 같은 시도인 정책 포함.
- 신청 마감일 오름차순(null 마지막) → 정책 ID 오름차순, 최대 20건.
- 후보 선정 전에 나이·소득·취업·무주택 조건으로 제외하지 않음. 자격 판정 및 F3 관심 조회는 Recommendation 책임.

오류: JSON·enum·날짜 형식 오류는 `400 / COMMON_001`, 필수값 누락·검증 실패는
`400 / COMMON_003`, 내부 오류는 `500 / SERVER_001`입니다.
JSON 본문 자체가 없거나 파싱할 수 없으면 `COMMON_001`입니다.

인증·노출: `/internal/**`는 Gateway 외부 라우팅에 노출하지 않습니다.
서비스 인증 수단·헤더는 인프라 담당자와 **미확정**이며 이번 구현에는 인증 검증을 추가하지 않습니다.
`X-Member-Id` 또는 사용자 JWT를 F2 인증 수단으로 간주하지 않습니다.
내부 API 구현 완료와 서비스 간 인증·네트워크 접근 통제 완료는 별도입니다.


## F5 관심 등록용 정책 존재·스냅샷 조회 (Issue #60)

Engagement 담당자와 필드·검증 기준·연결 방식 합의 완료(2026-11-03).
원본 `3e47bb1`의 `FavoriteService.createFavorite`와 현재 Engagement의 `policy_snapshot` 기준입니다.

- 호출: engagement → policy, `GET /internal/policies/{policyId}`
- policyId: 정책 ID 문자열. 요청 본문·회원 ID는 전달하지 않습니다.
- 응답: `ApiResponse<PolicySnapshotDTO>`, 성공은 `200 / SUCCESS_001`.

```json
{
  "isSuccess": true,
  "code": "SUCCESS_001",
  "message": "성공입니다.",
  "result": {
    "policyId": "POLICY-001",
    "policyName": "청년 월세 지원",
    "categories": ["MONTHLY_RENT"],
    "supportContent": "월세 지원 내용",
    "applyEndDate": "2026-11-30",
    "applyPeriodCode": "SPECIFIC_PERIOD",
    "applyUrl": "https://example.com/apply",
    "activeYn": true,
    "deleted": false
  }
}
```

| 필드 | 타입·규칙 |
|---|---|
| policyId, policyName | 문자열 |
| categories | MONTHLY_RENT / JEONSE / PURCHASE / PUBLIC_RENT / OTHER 배열. 순서에 의미 없음 |
| supportContent | 문자열 또는 null |
| applyEndDate | ISO 날짜 또는 null |
| applyPeriodCode | SPECIFIC_PERIOD / ALWAYS / CLOSED |
| applyUrl | 문자열 또는 null |
| activeYn | Boolean, 성공 시 true |
| deleted | Boolean, 성공 시 false |

정책 원본의 null·enum 값을 유지합니다. 카테고리 표시명은 Engagement에서 생성합니다.
favoriteId·관심 등록일은 Engagement 소유 데이터이므로 이 응답에 포함하지 않습니다.

### 유효성 및 오류

- 존재하고 삭제되지 않았으며 activeYn=true인 정책만 반환합니다.
- 없는 정책·삭제된 정책·비활성 정책은 모두 `404 / POLICY_001`입니다.
- 신청 시작 전·마감일 경과·CLOSED 상태도 위 조건을 만족하면 허용합니다. 기존 관심 등록은 신청 기간을 검사하지 않습니다.
- 내부 처리 오류는 `500 / SERVER_001`이며 정상 빈 결과나 정책 없음으로 바꾸지 않습니다.
- F2·정책 목록의 신청 기간 필터를 F5에 적용하지 않습니다.

### Engagement 연결 합의와 별도 범위

1. 관심 등록 시 F5로 정책 유효성과 스냅샷 조회.
2. 성공 응답으로 로컬 policy_snapshot을 생성·갱신한 뒤 기존 중복 검사·관심 등록 수행.
3. 스냅샷 반영과 관심 등록은 같은 로컬 트랜잭션으로 처리. 중복은 Engagement의 `409 / FAVORITE_001` 유지.
4. F5의 404·서버 오류·타임아웃·연결 실패 시 관심을 저장하지 않음. 장애를 정책 없음으로 바꾸지 않음.

위 소비·저장은 Engagement 담당 범위이며 이번 Policy 구현이 완료를 보장하지 않습니다.
관심 삭제는 원본이 정책 존재 여부만 검사하므로, F5를 그대로 재사용하지 않고 별도로 협의합니다.
이후 스냅샷 갱신은 policy-updated 이벤트와 연결하며, 이벤트 발행·소비와 순서/버전 처리는 별도 작업입니다.

인증: /internal/**는 Gateway 외부 라우팅에 노출하지 않습니다. 내부 인증 수단·헤더는 인프라와 미확정이며
이번 구현에는 인증 검증을 추가하지 않습니다. 사용자 JWT·X-Member-Id를 F5 인증 수단으로 가정하지 않습니다.
