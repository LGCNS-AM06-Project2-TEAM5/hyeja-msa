# Engagement API 명세서

기준일: 2026-11-02. 현재 브랜치의 구현 기준이며, 미구현 연동은 마지막 절에 구분한다.

## 1. 범위 및 인증

- 외부 API 6개: 관심 등록·해제·목록, 알림 조회·읽음·삭제. 알림 생성은 스케줄러만 담당한다.
- 현재 모든 업무 처리는 engagement DB 안에서 끝난다. Feign·Kafka 및 다른 서비스 내부 API는 구현하지 않았다.
- 회원 ID는 숫자만 저장한다. 회원 존재·탈퇴 여부는 확인하지 않는다.
- 정책 정보는 engagement DB의 `policy_snapshot` 읽기 모델이다. 관심 등록은 이 테이블에서 정책의 존재, 삭제 여부, `activeYn=true`를 확인한다. 정책 원본을 원격 조회하지 않는다.
- 실제 서비스에서는 Gateway가 JWT를 검증하고 사용자 식별 헤더를 설정한다. engagement 자체는 JWT를 검증하지 않으므로 내부 네트워크에서만 접근하도록 배포해야 한다.
- 서비스 포트는 실행 설정을 따른다. 실제 Gateway 주소·포트는 Gateway 설정을 따른다.

| 헤더 | 필수 | 규칙 |
|---|---|---|
| `X-Member-Id` | 모든 API | 양수 Long. 누락·숫자 변환 실패·0 이하는 401 |
| `Authorization: Bearer ...` | Gateway 접근 시 | Gateway가 검증. 로컬 직접 호출에는 불필요 |

모든 API의 회원 ID는 헤더를 사용한다. 모든 요청은 Body가 없다. 수동 알림 생성 API는 제공하지 않는다.

## 2. 공통 응답

모든 성공은 HTTP 200이며 아래 구조를 따른다. 삭제 성공도 204가 아닌 200이다.

```json
{
  "isSuccess": true,
  "code": "SUCCESS_001",
  "message": "성공입니다.",
  "result": null
}
```

`result`는 API별 객체·정수 또는 null이다. 일반 도메인 오류 예:

```json
{
  "isSuccess": false,
  "code": "NOTIFICATION_001",
  "message": "존재하지 않는 알림입니다.",
  "result": null
}
```

| HTTP | 코드 | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | 잘못된 쿼리·경로 타입, 필수 쿼리 누락, page<0, size<=0, 양수 제약 위반 등 |
| 401 | `COMMON_002` | 회원 식별 헤더 누락·잘못된 값 |
| 404 | `POLICY_001` | 로컬 정책 없음, 또는 등록 시 정책이 삭제·비활성 상태 |
| 404 | `FAVORITE_002` | 본인의 등록된 관심 정책 없음 |
| 404 | `NOTIFICATION_001` | 본인 알림 없음. 타 회원 알림도 동일 코드 |
| 409 | `FAVORITE_001` | 회원·정책 중복 관심 등록 |
| 500 | `SERVER_001` | 처리하지 못한 서버 오류 |

현재 회원 확인을 하지 않으므로 `MEMBER_001`은 반환하지 않는다. 필드/객체 검증 오류는 공통 예외 처리상 `COMMON_003`과 오류 맵을 사용할 수 있으나, 현재 API의 scalar 쿼리·경로 제약은 주로 `COMMON_001`로 반환된다.

날짜는 `YYYY-MM-DD`, 시각은 시간대 오프셋 없는 `YYYY-MM-DDTHH:mm:ss[.소수초]` 형식이다. D-7 기준일 계산은 Asia/Seoul이다.

## 3. 관심 정책

### 3.1 관심 등록

`POST /api/favorite/{policyId}`

| 위치 | 필드 | 타입 | 설명 |
|---|---|---|---|
| path | policyId | string | 로컬 읽기 모델의 정책 ID |

동작: 로컬 정책 유효성 확인 → 중복 확인 → 관심 저장. 성공 `result`는 아래 FavoriteItem이다. 동일 정책을 다시 등록하면 409. 해제 후 재등록은 새 favorite_id를 생성한다.

```json
{
  "isSuccess": true,
  "code": "SUCCESS_001",
  "message": "성공입니다.",
  "result": {
    "favorite_id": 1,
    "policy_id": "LOCAL-POLICY-001",
    "policy_name": "로컬 테스트 월세 지원",
    "category_codes": ["MONTHLY_RENT"],
    "category_names": ["월세"],
    "support_content": "Postman 테스트용 정책 데이터",
    "apply_end_date": "2026-11-09",
    "apply_period_code": "SPECIFIC_PERIOD",
    "apply_url": "https://example.com/apply",
    "created_at": "2026-11-02T15:00:00"
  }
}
```

예시 날짜·ID는 설명용이며 실제 테스트 시 로컬 policy_snapshot에 데이터를 준비해야 한다.

FavoriteItem 필드:

| 필드 | JSON 타입 | 설명 |
|---|---|---|
| favorite_id | integer(int64) | 관심 등록 ID |
| policy_id | string | 정책 ID |
| policy_name | string | 로컬 읽기 모델의 정책명 |
| category_codes | string[] | 정책 분류 enum 이름. 집합이므로 배열 순서 보장 없음 |
| category_names | string[] | 코드 이름순 정렬 후 변환한 분류명 |
| support_content | string/null | 지원 내용 |
| apply_end_date | string(date)/null | 로컬 읽기 모델의 마감일 |
| apply_period_code | string | `SPECIFIC_PERIOD`, `ALWAYS`, `CLOSED`. 숫자 외부 API 코드가 아님 |
| apply_url | string/null | 신청 URL |
| created_at | string(datetime) | 관심 등록 시각 |

분류 코드: `MONTHLY_RENT`=월세, `JEONSE`=전세, `PURCHASE`=청약·구입, `PUBLIC_RENT`=공공임대, `OTHER`=기타 주거. category_names와 category_codes를 배열 인덱스로 짝지으면 안 된다.

오류: 공통 인증 오류, 404 `POLICY_001`, 409 `FAVORITE_001`.

### 3.2 관심 목록·검색

`GET /api/favorite`

| query | 타입 | 필수 | 기본값 | 제약 |
|---|---|---|---|---|
| keyword | string | 아니오 | 없음 | 정책명 또는 지원 내용 부분 검색. 앞뒤 공백 제거, 빈 문자열이면 전체 조회 |
| page | integer | 아니오 | 0 | 0 이상 |
| size | integer | 아니오 | 8 | 양수. 현재 별도 상한 없음 |

예: `/api/favorite?keyword=월세&page=0&size=8`

본인의 관심만 조회한다. `created_at DESC, favorite_id DESC` 정렬. `%`, `_`, `!`는 검색 와일드카드가 아닌 문자로 처리한다. 조회 시 정책의 현재 active/deleted 상태를 추가 필터링하지 않으며, 로컬 읽기 모델의 저장 값을 반환한다.

성공 `result`:

```json
{
  "favorites": [],
  "page": 0,
  "size": 8,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

favorites 원소는 FavoriteItem. totalElements는 검색 조건에 맞는 전체 관심 수, totalPages는 해당 size 기준 전체 페이지 수, hasNext는 다음 페이지 유무다. 데이터가 없거나 범위를 벗어난 page는 200과 빈 배열을 반환한다. 범위 밖 page라도 전체 건수는 유지한다.

오류: 공통 인증 오류, 잘못된 page/size는 400.

### 3.3 관심 해제

`DELETE /api/favorite/{policyId}`

path policyId는 string. 헤더 회원의 관심 행을 영구 삭제한다. 성공 `result: null`.

오류: 로컬 정책 자체가 없으면 404 `POLICY_001`; 로컬 정책은 있지만 본인 관심이 없으면 404 `FAVORITE_002`. 반복 삭제도 404이며, 다른 회원의 관심에는 영향을 주지 않는다. 생성된 알림을 함께 삭제하지 않는다.

## 4. 알림

### 4.1 알림 목록

`GET /api/notification`

| query | 타입 | 필수 | 기본값 | 제약 |
|---|---|---|---|---|
| page | integer | 아니오 | 0 | 0 이상 |
| size | integer | 아니오 | 8 | 양수. 현재 별도 상한 없음 |

본인의 알림만 조회한다. `created_at DESC, notification_id DESC` 정렬. 조회만으로 읽음 처리하지 않는다.

성공 `result` 예:

```json
{
  "notifications": [
    {
      "notification_id": 1,
      "member_id": 1,
      "policy_id": "LOCAL-POLICY-001",
      "policy_name": "로컬 테스트 월세 지원",
      "content": "관심 정책의 신청 마감이 일주일 남았어요",
      "read_yn": false,
      "apply_end_date": "2026-11-09",
      "created_at": "2026-11-02T15:00:00"
    }
  ],
  "unread_count": 1,
  "page": 0,
  "size": 8,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

NotificationItem 필드:

| 필드 | JSON 타입 | 설명 |
|---|---|---|
| notification_id | integer(int64) | 알림 ID |
| member_id | integer(int64) | 알림 수신 회원 ID |
| policy_id | string | 정책 ID |
| policy_name | string | 로컬 읽기 모델의 정책명 |
| content | string | 현재 고정 문구: 관심 정책의 신청 마감이 일주일 남았어요 |
| read_yn | boolean | 읽음 여부. 생성 시 false |
| apply_end_date | string(date) | 알림 생성 당시 저장한 마감일. 정책 변경 시에도 유지 |
| created_at | string(datetime) | 알림 생성 시각 |

unread_count는 현재 페이지 수가 아니라 해당 회원의 삭제되지 않은 전체 미읽음 알림 수다. 빈 목록은 notifications=[]이고, 페이징 필드는 관심 목록과 같은 의미다.

오류: 공통 인증 오류, 잘못된 page/size는 400.

### 4.2 알림 읽음

`PATCH /api/notification/{notificationId}/read`

path notificationId는 양수 Long. 본인 알림의 read_yn을 true로 바꾸고, 성공 `result`로 NotificationItem을 반환한다. 이미 읽은 알림도 200으로 반환한다. 읽음 후 목록의 unread_count가 감소한다.

오류: 400 잘못된 ID; 404 `NOTIFICATION_001`(없음·삭제됨·타 회원 소유). 공통 인증 오류 적용.

### 4.3 알림 삭제

`DELETE /api/notification/{notificationId}`

path notificationId는 양수 Long. 본인 알림 행을 영구 삭제한다. 성공 `result: null`. 반복 삭제 또는 타 회원 알림은 404 `NOTIFICATION_001`. 미읽음 알림을 삭제하면 unread_count에서도 제외한다.

오류: 400 잘못된 ID, 404 `NOTIFICATION_001`, 공통 인증 오류.

### 4.4 자동 생성 — HTTP API 아님

`NotificationScheduler`: cron `0 5 0 * * *`, zone `Asia/Seoul`. 매일 00:05에 전체 회원의 로컬 관심을 대상으로 실행한다. test 프로필에서는 비활성화된다.

대상: 관심이 삭제되지 않았고, 로컬 정책이 삭제되지 않았으며 activeYn=true이고 applyEndDate가 서울 기준 오늘 +7일인 경우다. 현재 apply_period_code에 대한 별도 필터는 없고 회원 탈퇴 상태도 조회하지 않는다.

관심은 회원·정책, 알림은 회원·정책·마감일에 DB 유일 제약을 둔다. 알림을 hard delete한 뒤 같은 기준일에 다시 생성하면 재생성될 수 있다. 다중 인스턴스 동시 실행 시 DB 중복 저장은 방지하지만 충돌 트랜잭션은 실패할 수 있으므로 현재 스케줄러는 한 인스턴스 실행을 전제로 한다.

## 5. 실행 및 검증 범위

기본 dev 프로필은 MariaDB를 사용하며 테스트 데이터를 자동 생성하지 않는다. ENGAGEMENT_DB_URL, ENGAGEMENT_DB_USERNAME, ENGAGEMENT_DB_PASSWORD를 실행 환경에 설정한다. 정책 원본 연동 전에는 로컬 policy_snapshot에 정책 데이터를 준비해야 관심을 등록할 수 있다.

```powershell
./services/engagement-service/gradlew.bat -p services/engagement-service test --no-daemon
```

테스트는 H2를 사용한다. FavoriteServiceTest는 관심 중복·해제·재등록·검색·회원 격리와 HTTP JSON·인증을, NotificationServiceTest는 서울 날짜 기준 스케줄러 호출·D-7 계산·중복 방지·소유권·읽음·삭제 및 수동 생성 경로 제거를 확인한다. 생성은 스케줄러가 담당하며 테스트에서는 자정 대기 없이 같은 메서드를 호출한다. 실제 Gateway/Feign/Kafka/MariaDB 전체 연동과 자정 자동 실행은 검증 범위 밖이다.
## 6. Issue #6 상태와 추후 연동 계약

[Issue #6](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-msa/issues/6)에 기재된 외부 API 중 수동 생성을 제외한 6개 및 스케줄러·정책 읽기 모델을 구성했다. 수동 생성은 사용자 결정에 따라 제거했다. 내부 관심 여부 조회 F3·F4와 정책 확인 F5는 아직 미구현이므로 이슈 전체 완료로 처리하지 않는다.

현재 member는 스칼라 ID이고 정책 원본 DB와의 FK는 없다. 다만 favorite/notification의 policy는 같은 engagement DB의 policy_snapshot에 JPA 연관관계·FK를 유지한다. 이슈의 ‘ID만 저장’이 로컬 읽기 모델 관계까지 금지하는 의미인지는 팀에서 확정해야 한다. 현재 구조는 원격 DB JOIN을 하지 않는다.

아래는 합의할 내용이며 확정된 경로·이벤트 스키마가 아니다. [서비스 간 계약](README.md)과 함께 갱신한다.

| 연결 | 현재 연결 지점 | 합의할 내용 |
|---|---|---|
| engagement → policy, F5 Feign | FavoriteService.createFavorite의 로컬 정책 조회 전 | 내부 경로·단건 DTO, 존재/삭제/비활성 의미, 반환할 스냅샷 필드, 404/장애 응답, 타임아웃·재시도. 유효성 실패 또는 장애 시 저장하지 않기 |
| policy/recommendation → engagement, F3·F4 Feign | FavoriteRepository.findActivePolicyIds | 내부 일괄 조회 경로·요청/응답 DTO, 인증된 회원 ID 전달 방식, 요청 최대 ID 수, 빈 목록·없는 ID 처리, 실패 시 하트 표시 정책. Repository만 존재하고 API는 미구현 |
| policy → engagement, policy-updated Kafka | policy_snapshot 갱신, 검색·목록·D-7 대상 판단 | 필드·enum 표현, eventId/eventVersion/occurredAt, key=policyId, 정책 변경 버전·역순 이벤트 처리, 삭제/비활성 표현, 최초 데이터 적재·재동기화, 발행 신뢰성(Outbox 등), 재시도·DLT |
| user → engagement, member-withdrawn Kafka | 회원 ID 기준 favorite/notification 삭제 | memberId와 공통 이벤트 메타데이터, DB 커밋 후 발행, eventId 중복 처리, 원자적 정리·재시도·DLT. 탈퇴 이벤트와 관심 등록이 경합하거나 이벤트가 늦게 도착할 때 재생성을 막을지 결정 |
| gateway → engagement | GatewayIdentityConfig 및 controller의 헤더 수신 | 회원 ID 헤더 계약, 외부 주입 헤더 제거, 서비스 직접 접근 제한, Gateway 통합 테스트 |

정책명은 읽기 모델에서 조회하므로 갱신 이벤트 적용 후 목록·알림 응답에 반영된다. 알림의 마감일은 생성 당시 값이다. 정책 변경·삭제를 이유로 기존 관심·알림을 자동 삭제하는 기능은 추가하지 않는다. 회원 탈퇴는 이벤트 반영 전까지 로컬에서 알 수 없다는 지연 특성을 합의해야 한다.

알림 생성·조회·읽음·삭제에는 추가 Feign 호출이 필요하지 않다. Kafka가 로컬 데이터 갱신·정리를 담당하면 해당 데이터만으로 처리할 수 있다. 메일 발송용 notification-created 이벤트는 이번 구현 밖이다.

현재 변경은 독립 기능 단계의 커밋으로 저장할 수 있다. 예: `feat : 관심 정책 및 D-7 알림 독립 기능 구현 (#6)`. F3·F4·F5 및 이벤트 연동이 끝나기 전에는 이슈 자동 종료 문구(Closes #6)를 사용하지 않는다.
