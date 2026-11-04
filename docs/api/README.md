# HYEJA MSA API 명세

## 1. 개요

팀이 작성·검토를 마친 최종 API 명세서(Excel, 문서 번호 `HYEJA-MSA-API-001`, v0.1 최종, 기준 코드 `hyeja-msa` develop 2026-11-04)의 내용을 옮긴 문서입니다. Excel 원본은 저장소에 넣지 않았습니다.

- 업무 API 32개: 외부 28개(관리자 2개 포함) + 내부 4개. 서비스별로 user 11, policy 13, engagement 7, recommendation 1.
- 내부 OpenFeign 계약 F1~F6은 논리 호출 6개이고, 실제 내부 엔드포인트는 4개를 같이 씁니다(USR-11=F1·F6, POL-12=F2, POL-13=F5, ENG-07=F3·F4).
- Kafka 이벤트 계약(9장)은 REST가 아니라 별도 통신 방식이라 보조 절로 둡니다.
- 계약이 바뀌면 이 문서부터 고치고 같은 PR에서 코드를 바꿉니다. 문서와 코드가 다르면 코드가 기준이며 차이를 이 문서에 적습니다.

목차: [2. 공통 규약](#2-공통-규약) · [3. API 목록](#3-api-목록-32개) · [4. user](#4-user-service-api) · [5. policy](#5-policy-service-api) · [6. engagement](#6-engagement-service-api) · [7. recommendation](#7-recommendation-service-api) · [8. OpenFeign F1~F6](#8-내부-openfeign-통신-f1f6) · [9. Kafka](#9-kafka-이벤트-계약) · [10. 오류 코드](#10-오류-코드) · [11. 참고](#11-참고-문서)

## 2. 공통 규약

| 항목 | 규약 |
|---|---|
| Base URL | `http://localhost:8000` (API Gateway). 외부 요청은 모두 Gateway로 보냅니다. 서비스 포트는 외부에 열지 않습니다 |
| 인증 방식 | `Authorization: Bearer {accessToken}`. [USR-04](#usr-04) 로그인에서 받은 JWT(HS256, `sub`=memberId, `role`, 5시간) |
| Gateway가 넘기는 헤더 | JWT 서명·만료와 Redis 로그아웃 블랙리스트를 확인한 뒤 `X-Member-Id`, `X-Member-Role`을 다시 넣습니다. 클라이언트가 보낸 같은 이름의 헤더는 지웁니다 |
| Engagement 인증 | engagement는 `X-Member-Id`를 쓰지 않고, Gateway가 별도 키(`GATEWAY_ENGAGEMENT_SECRET`)로 만든 내부 JWT `X-Gateway-Assertion`(수명 2분)을 검증해 회원을 식별합니다. [상세](../architecture/gateway-engagement-auth.md) |
| 인증 표기 | **비로그인** 토큰 없이 호출 · **로그인** 토큰 필요, 없으면 401 `COMMON_002` · **로그인 선택** 토큰이 있으면 회원 기준 정보가 더해짐 · **ADMIN** 관리자만, 일반 회원은 403 `COMMON_004` · **내부** `/internal/**`, Gateway에 라우팅이 없어 외부에서 호출 불가 |
| 잘못된 토큰 | 공개 API라도 잘못된 JWT를 함께 보내면 401 `COMMON_002`입니다 |
| 내부 API | 서비스끼리 Eureka 서비스 이름으로 OpenFeign 호출합니다. 별도 서비스 인증 토큰(`X-Internal-Token` 등)은 없고 내부 네트워크에서만 호출합니다. 사용자 JWT·`X-Member-Id`를 내부 API 인증 수단으로 쓰지 않습니다 |
| Content-Type | `application/json; charset=UTF-8` |
| 날짜·시간 | REST 응답은 ISO-8601 문자열, 한국 시간, 시간대 오프셋 없음. 날짜 `2026-11-30`, 시간 `2026-11-04T10:53:30`(소수 초가 붙을 수 있음). "오늘"은 `Asia/Seoul` 기준. 예외: Kafka 이벤트의 `occurredAt`은 `OffsetDateTime`이라 `2026-11-03T17:30:00+09:00`처럼 오프셋을 포함합니다([9장](#9-kafka-이벤트-계약)) |
| JSON 필드 이름 | 서비스마다 1차 응답을 그대로 따릅니다. user·recommendation과 policy 상세·카드뉴스·용어는 camelCase, policy 목록(POL-01·03)의 정책 항목과 engagement 항목은 snake_case입니다. 표에는 실제 응답 이름을 적었으며 통일하지 않습니다 |

공통 응답 형식(성공은 모두 HTTP 200):

```json
{
  "isSuccess": true,
  "code": "SUCCESS_001",
  "message": "성공입니다.",
  "result": { }
}
```

- `result`는 API마다 객체, 배열, 문자열 또는 `null`입니다. 각 API의 "응답" 표에 형태를 적었습니다.
- 실패하면 `isSuccess=false`이고 `code`·`message`는 [10장](#10-오류-코드) 값입니다. `result`는 보통 `null`이며, 예외는 `COMMON_003`(필드별 메시지 맵), `VERIFY_001`(`remainingAttempts`), `POLICY_002`(`stoppedPage`·`savedCount`)입니다.
- 목록 응답(POL-01·03, ENG-01·04)은 `result`에 `page`(0부터), `size`, `totalElements`, `totalPages`, `hasNext`를 함께 내려줍니다.
- 처리되지 않은 서버 오류는 모든 서비스에서 500 `SERVER_001`입니다.

운영 헬스 체크는 32개 업무 API에 넣지 않습니다. Gateway의 `/_infra/health/user`·`/policy`·`/engagement`·`/recommendation`(docker 프로필, 로그인 토큰 필요)이 각 서비스의 `/health-check`로 연결되며, 관리자 운영 화면과 배포 후 확인(`scripts/deploy.sh`)에 씁니다. `/api/health`는 Gateway 인증 허용 목록에만 있고 라우트와 컨트롤러가 없어 제공되지 않습니다.

## 3. API 목록 (32개)

| API ID | 서비스 | 구분 | 기능 | Method | URI | 인증 | 내부 계약 |
|---|---|---|---|---|---|---|---|
| [USR-01](#usr-01) | user | 외부 | 이메일 인증 코드 발송 | POST | `/api/members/email-verifications` | 비로그인 | |
| [USR-02](#usr-02) | user | 외부 | 이메일 인증 코드 확인 | POST | `/api/members/email-verifications/confirmation` | 비로그인 | |
| [USR-03](#usr-03) | user | 외부 | 회원가입(내 조건 함께 저장) | POST | `/api/members` | 비로그인 | |
| [USR-04](#usr-04) | user | 외부 | 로그인 | POST | `/api/members/login` | 비로그인 | |
| [USR-05](#usr-05) | user | 외부 | 로그아웃 | POST | `/api/members/logout` | 로그인 | |
| [USR-06](#usr-06) | user | 외부 | 내 계정 조회 | GET | `/api/members/me` | 로그인 | |
| [USR-07](#usr-07) | user | 외부 | 회원 탈퇴 | PATCH | `/api/members/me/delete` | 로그인 | |
| [USR-08](#usr-08) | user | 외부 | 이메일 찾기 | GET | `/api/members/find-email` | 비로그인 | |
| [USR-09](#usr-09) | user | 외부 | 내 조건 조회 | GET | `/api/members/me/profile` | 로그인 | |
| [USR-10](#usr-10) | user | 외부 | 내 조건 수정 | PATCH | `/api/members/me/profile` | 로그인 | |
| [USR-11](#usr-11) | user | 내부 | 회원 조건 조회 | GET | `/internal/members/{memberId}/profile` | 내부 | F1·F6 |
| [POL-01](#pol-01) | policy | 외부 | 비로그인 정책 목록 | GET | `/api/policies/housing` | 비로그인 | |
| [POL-02](#pol-02) | policy | 외부 | 정책 상세(로그인 시 자격 판정) | GET | `/api/policies/{policyId}` | 로그인 선택 | F4·F6 |
| [POL-03](#pol-03) | policy | 외부 | 내 조건 정책 목록 | GET | `/api/policies/housing/me` | 로그인 | F4·F6 |
| [POL-04](#pol-04) | policy | 외부 | 비로그인 홈 카드뉴스 | GET | `/api/policies/card-news/guest` | 비로그인 | |
| [POL-05](#pol-05) | policy | 외부 | 로그인 회원 홈 카드뉴스 | GET | `/api/policies/card-news` | 로그인 | F6 |
| [POL-06](#pol-06) | policy | 외부 | 카드뉴스 상세 | GET | `/api/policies/card-detail/{policyId}` | 로그인 선택 | F4 |
| [POL-07](#pol-07) | policy | 외부 | 지역 목록 | GET | `/api/regions` | 비로그인 | |
| [POL-08](#pol-08) | policy | 외부 | 용어 목록 | GET | `/api/terms` | 비로그인 | |
| [POL-09](#pol-09) | policy | 외부 | 용어 상세 | GET | `/api/terms/{termId}` | 비로그인 | |
| [POL-10](#pol-10) | policy | 관리자 | 정책 수집·동기화 | POST | `/api/policies/sync` | ADMIN | |
| [POL-11](#pol-11) | policy | 관리자 | 정책 이벤트 발행 상태 조회 | GET | `/api/policies/admin/events` | ADMIN | |
| [POL-12](#pol-12) | policy | 내부 | 정책 후보 조회 | POST | `/internal/policies/candidates` | 내부 | F2 |
| [POL-13](#pol-13) | policy | 내부 | 관심 등록용 정책 스냅샷 조회 | GET | `/internal/policies/{policyId}` | 내부 | F5 |
| [ENG-01](#eng-01) | engagement | 외부 | 관심 정책 목록 | GET | `/api/favorite` | 로그인 | |
| [ENG-02](#eng-02) | engagement | 외부 | 관심 정책 등록 | POST | `/api/favorite/{policyId}` | 로그인 | F5 |
| [ENG-03](#eng-03) | engagement | 외부 | 관심 정책 해제 | DELETE | `/api/favorite/{policyId}` | 로그인 | |
| [ENG-04](#eng-04) | engagement | 외부 | 알림 목록 | GET | `/api/notification` | 로그인 | |
| [ENG-05](#eng-05) | engagement | 외부 | 알림 읽음 처리 | PATCH | `/api/notification/{notificationId}/read` | 로그인 | |
| [ENG-06](#eng-06) | engagement | 외부 | 알림 삭제 | DELETE | `/api/notification/{notificationId}` | 로그인 | |
| [ENG-07](#eng-07) | engagement | 내부 | 관심 정책 ID 일괄 조회 | POST | `/internal/favorites/active-policy-ids` | 내부 | F3·F4 |
| [REC-01](#rec-01) | recommendation | 외부 | 회원 맞춤 정책 검색(AI) | GET | `/api/policies/search` | 로그인 | F1·F2·F3 |

Gateway 라우팅은 `GET /api/policies/search` → recommendation이 `/api/policies/**` → policy보다 먼저 매칭됩니다. 라우팅과 인증 규칙은 [gateway-service](../../infra/gateway-service/README.md)에 있습니다. 알림은 스케줄러(매일 00:05 Asia/Seoul)가 만들며 수동 생성 API는 없습니다.

## 4. User Service API

<a id="usr-01"></a>
### USR-01 이메일 인증 코드 발송

`POST /api/members/email-verifications` · 비로그인

회원가입에 쓸 이메일로 숫자 6자리 인증 코드를 보냅니다. 코드는 5분 동안 유효하고, 같은 이메일은 60초 뒤 다시 요청할 수 있습니다. 이미 가입된 이메일에는 보내지 않습니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Body | `email` | String | 필수 | 인증 코드를 받을 이메일 |
| 응답 | `expiresInSeconds` | Long | null 아님 | 인증 코드 남은 유효 시간(초), 예 `300` |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_003` | 이메일 누락 또는 이메일 형식 오류 |
| 409 | `MEMBER_002` | 이미 가입된 이메일 |
| 429 | `VERIFY_004` | 같은 이메일로 60초 안에 재요청 |
| 429 | `VERIFY_005` | 인증 실패 횟수 초과로 1시간 잠긴 이메일 |
| 500 | `MAIL_001` | SMTP 메일 발송 실패 |
| 500 | `SERVER_001` | 서버 내부 오류 |

<a id="usr-02"></a>
### USR-02 이메일 인증 코드 확인

`POST /api/members/email-verifications/confirmation` · 비로그인

숫자 6자리 코드를 확인합니다. 성공하면 그 이메일을 30분 동안 인증 완료 상태로 저장합니다. 코드를 5회 틀리면 1시간 동안 인증이 잠깁니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Body | `email` | String | 필수 | 인증 코드를 요청한 이메일 |
| Body | `code` | String | 필수 | 숫자 6자리 인증 코드 |
| 응답 | `verified` | Boolean | null 아님 | 인증 성공 여부 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_003` | 이메일·코드 누락 또는 코드가 숫자 6자리가 아님 |
| 400 | `VERIFY_001` | 코드 불일치. 실패 응답 `result`에 `remainingAttempts` 포함 |
| 400 | `VERIFY_002` | 코드가 없거나 5분 유효 시간 만료 |
| 429 | `VERIFY_005` | 5회 실패로 1시간 잠김 |
| 500 | `SERVER_001` | 서버 내부 오류 |

<a id="usr-03"></a>
### USR-03 회원가입(내 조건 함께 저장)

`POST /api/members` · 비로그인

이메일 인증 완료 후 30분 안에 회원 정보와 내 조건을 하나의 트랜잭션으로 저장합니다. 가입 계정의 역할은 `USER`입니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Body | `email` | String | 필수 | 인증을 완료한 이메일(최대 100자) |
| Body | `password` | String | 필수 | 영문·숫자·특수문자 포함 8~20자 |
| Body | `nickname` | String | 필수 | 최대 20자 |
| Body | `profile.birth` | LocalDate | 필수 | 오늘보다 이전 날짜 |
| Body | `profile.regionCode` | String | 필수 | 시군구 코드 5자리 |
| Body | `profile.employmentCode` | enum | 필수 | 취업 상태 |
| Body | `profile.houselessYn` | Boolean | 필수 | 무주택 여부 |
| Body | `profile.marriageCode` | enum | 선택, 미입력 시 null | 혼인 상태 |
| Body | `profile.incomeRangeCode` | enum | 선택, 미입력 시 null | 연소득 구간 |
| Body | `profile.educationCode` | enum | 선택, 미입력 시 null | 최종 학력 |
| Body | `profile.housingType` | enum | 선택, 미입력 시 null | 주거 형태 |
| 응답 | `memberId` | Long | null 아님 | 생성된 회원 ID |
| 응답 | `email`, `nickname` | String | null 아님 | 가입 이메일·닉네임 |
| 응답 | `createdAt` | LocalDateTime | null 아님 | 회원 생성 시각 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | JSON·날짜 형식 오류 또는 존재하지 않는 enum 값 |
| 400 | `COMMON_003` | 필수값 누락 또는 입력값 검증 실패 |
| 400 | `VERIFY_003` | 이메일 인증을 하지 않았거나 인증 후 30분이 지남 |
| 409 | `MEMBER_002` / `MEMBER_003` | 이메일 또는 닉네임 중복 |
| 404 | `REGION_001` | user_db 지역 복사본에 없는 시군구 코드 |
| 500 | `SERVER_001` | 서버 내부 오류 |

<a id="usr-04"></a>
### USR-04 로그인

`POST /api/members/login` · 비로그인

이메일과 비밀번호를 확인한 뒤 5시간 유효한 JWT `accessToken`을 발급합니다. 없는 회원·탈퇴한 회원·잘못된 비밀번호는 보안을 위해 같은 오류로 응답합니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Body | `email` | String | 필수 | 가입한 이메일 |
| Body | `password` | String | 필수 | 현재 비밀번호 |
| 응답 | `accessToken` | String | null 아님 | 5시간 유효한 JWT |
| 응답 | `memberId` | Long | null 아님 | 회원 ID |
| 응답 | `nickname` | String | null 아님 | 닉네임 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_003` | 이메일 또는 비밀번호 누락 |
| 401 | `MEMBER_005` | 이메일·비밀번호 불일치, 없거나 탈퇴한 회원 |
| 500 | `SERVER_001` | 서버 내부 오류 |

<a id="usr-05"></a>
### USR-05 로그아웃

`POST /api/members/logout` · 로그인

요청에 쓴 JWT를 토큰 만료 시각까지 Redis 블랙리스트에 저장해 다시 쓸 수 없게 합니다. Gateway는 이후 이 토큰을 401로 막습니다.

- Request: Header `Authorization` 필수(Bearer + accessToken)
- 응답: `result`는 `null`

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | Authorization 없음, 토큰 만료·위조·블랙리스트 |
| 500 | `SERVER_001` | 서버 내부 오류 |

<a id="usr-06"></a>
### USR-06 내 계정 조회

`GET /api/members/me` · 로그인

Gateway가 JWT에서 확인해 넘긴 memberId로 계정 정보를 조회합니다. 탈퇴한 회원은 조회하지 않습니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Header | `Authorization` | String | 필수 | Bearer + accessToken |
| 응답 | `memberId` | Long | null 아님 | 회원 ID |
| 응답 | `email`, `nickname` | String | null 아님 | 이메일·닉네임 |
| 응답 | `createdAt` | LocalDateTime | null 아님 | 가입 시각 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | Authorization 없음 또는 유효하지 않은 토큰 |
| 404 | `MEMBER_001` | 없거나 탈퇴한 회원 |
| 500 | `SERVER_001` | 서버 내부 오류 |

<a id="usr-07"></a>
### USR-07 회원 탈퇴

`PATCH /api/members/me/delete` · 로그인

현재 비밀번호를 확인한 뒤 회원과 프로필을 소프트 삭제하고, 사용한 JWT를 블랙리스트에 등록합니다. 같은 트랜잭션에서 `member-withdrawn` Outbox 행을 저장하고, Relay가 커밋 후 Kafka로 보내면 engagement가 관심 정책과 알림을 정리합니다([9장](#9-kafka-이벤트-계약)).

- Request: Header `Authorization` 필수, Body `password`(String, 필수, 현재 비밀번호)
- 응답: `result`는 `null`

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_003` | 비밀번호 누락 |
| 400 | `MEMBER_006` | 현재 비밀번호 불일치 |
| 401 | `COMMON_002` | Authorization 없음 또는 유효하지 않은 토큰 |
| 404 | `MEMBER_001` | 없거나 이미 탈퇴한 회원 |
| 500 | `SERVER_001` | 서버 내부 오류 |

<a id="usr-08"></a>
### USR-08 이메일 찾기

`GET /api/members/find-email` · 비로그인

닉네임과 생년월일이 모두 일치하는 활성 회원의 이메일을 일부 마스킹해 돌려줍니다. 탈퇴한 회원은 검색하지 않습니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Query | `nickname` | String | 필수 | 가입 닉네임 |
| Query | `birth` | LocalDate | 필수 | 생년월일(`yyyy-MM-dd`) |
| 응답 | `email` | String | null 아님 | 마스킹된 이메일, 예 `hye***@example.com` |
| 응답 | `joinedAt` | LocalDate | null 아님 | 가입일 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | nickname 누락 또는 birth 날짜 형식 오류 |
| 404 | `MEMBER_004` | 닉네임·생년월일이 일치하는 활성 회원 없음 |
| 500 | `SERVER_001` | 서버 내부 오류 |

<a id="usr-09"></a>
### USR-09 내 조건 조회

`GET /api/members/me/profile` · 로그인

내 조건 8개를 조회합니다. 만 나이 `age`는 저장하지 않고 조회할 때마다 `birth`로 계산합니다. 선택 항목을 등록하지 않았으면 해당 코드와 이름은 `null`입니다.

| 응답 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `birth` | LocalDate | 아님 | 생년월일 |
| `age` | Integer | 아님 | 만 나이(birth로 계산) |
| `regionCode` / `regionName` | String | 아님 / 가능 | 시군구 코드 5자리 / 지역명(지역 복사본에 없으면 null) |
| `employmentCode` / `employmentName` | enum / String | 아님 | 취업 상태 |
| `houselessYn` | Boolean | 아님 | 무주택 여부 |
| `marriageCode` / `marriageName` | enum / String | 가능 | 혼인 상태(선택) |
| `incomeRangeCode` / `incomeRangeName` | enum / String | 가능 | 연소득 구간(선택) |
| `educationCode` / `educationName` | enum / String | 가능 | 최종 학력(선택) |
| `housingType` / `housingTypeName` | enum / String | 가능 | 주거 형태(선택) |
| `updatedAt` | LocalDateTime | 아님 | 조건 마지막 수정 시각 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | Authorization 없음 또는 유효하지 않은 토큰(만료·위조) |
| 404 | `MEMBER_001` | 토큰의 회원이 없거나 탈퇴함 |
| 404 | `PROFILE_001` | 회원은 있지만 등록된 조건이 없음(삭제된 조건 포함) |
| 500 | `SERVER_001` | 서버 내부 오류 |

<a id="usr-10"></a>
### USR-10 내 조건 수정

`PATCH /api/members/me/profile` · 로그인

내 조건 8개를 **통째로 교체**합니다. 부분 수정이 아니므로 선택 항목을 `null`로 보내거나 빠뜨리면 기존 값이 지워집니다. 지역 코드가 region 테이블에 없으면 아무것도 바꾸지 않고 404를 돌려줍니다. 성공 응답은 [USR-09](#usr-09)와 같은 형식입니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Header | `Authorization` | String | 필수 | Bearer + accessToken |
| Body | `birth` | LocalDate | 필수 | 오늘 이전 날짜 |
| Body | `regionCode` | String | 필수 | 시군구 코드 5자리, region 테이블에 있어야 함 |
| Body | `employmentCode` | enum | 필수 | 취업 상태 |
| Body | `houselessYn` | Boolean | 필수 | 무주택 여부 |
| Body | `marriageCode` | enum | 선택 | `SINGLE`, `MARRIED`. null이면 기존 값 삭제 |
| Body | `incomeRangeCode` | enum | 선택 | 연소득 구간. null이면 기존 값 삭제 |
| Body | `educationCode` | enum | 선택 | 최종 학력. null이면 기존 값 삭제 |
| Body | `housingType` | enum | 선택 | `PARENTS`, `MONTHLY_RENT`, `JEONSE`, `OWNED`. null이면 기존 값 삭제 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_003` | 필수값 누락 또는 검증 실패. `result`에 필드별 메시지(예 `{"birth": "생년월일은 필수입니다."}`) |
| 400 | `COMMON_001` | JSON 형식 오류, 없는 enum 값, 날짜 형식 오류 |
| 401 | `COMMON_002` | Authorization 없음 또는 유효하지 않은 토큰 |
| 404 | `MEMBER_001` | 토큰의 회원이 없거나 탈퇴함 |
| 404 | `PROFILE_001` | 등록된 조건이 없음 |
| 404 | `REGION_001` | regionCode가 region 테이블에 없음(조건은 바뀌지 않음) |

<a id="usr-11"></a>
### USR-11 회원 조건 조회 (내부, F1·F6)

`GET /internal/members/{memberId}/profile` · 내부 · recommendation(F1)·policy(F6)가 호출

회원의 조건을 자격 판정용 코드값으로 돌려줍니다. Gateway는 `/api/members/**`만 라우팅하므로 외부에 노출되지 않고, user-service `SecurityConfig`가 `/internal/**`을 JWT 없이 허용합니다.

- 나이는 계산하지 않고 `birth`만 반환합니다. 판정 기준 날짜는 호출하는 쪽이 정합니다.
- 한글 이름 없이 enum 코드만 내려줍니다. 지역만 이름(`sigunguName`)을 함께 주며, 필드명과 enum 값은 1차 코드(hyeja-backend)의 Profile과 같습니다.
- `sigunguName`은 user_db의 지역 읽기 복사본에서 채웁니다. 복사본에 코드가 없으면(행정구역 변경 등) `null`이고, 이때도 `regionCode`는 채워집니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Path | `memberId` | Long | 필수 | 조회할 회원 ID |
| 응답 | `memberId` | Long | 아님 | 회원 ID |
| 응답 | `birth` | LocalDate | 아님 | 생년월일 |
| 응답 | `region.regionCode` / `region.sigunguName` | String | 아님 / 가능 | 시군구 코드 / 지역명 |
| 응답 | `employmentCode` | enum | 아님 | 취업 상태 코드 |
| 응답 | `houselessYn` | Boolean | 아님 | 무주택 여부 |
| 응답 | `marriageCode`, `incomeRangeCode`, `educationCode`, `housingType` | enum | 가능 | 미등록이면 null |

```json
{
  "isSuccess": true,
  "code": "SUCCESS_001",
  "message": "성공입니다.",
  "result": {
    "memberId": 1,
    "birth": "2000-05-15",
    "region": { "regionCode": "11440", "sigunguName": "서울특별시 마포구" },
    "employmentCode": "UNEMPLOYED",
    "houselessYn": true,
    "marriageCode": "SINGLE",
    "incomeRangeCode": "UNDER_2000",
    "educationCode": null,
    "housingType": "MONTHLY_RENT"
  }
}
```

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | memberId가 숫자가 아님(예 `/internal/members/abc/profile`) |
| 404 | `MEMBER_001` | 없거나 탈퇴한 회원 |
| 404 | `PROFILE_001` | 회원은 있지만 등록된 조건이 없음(프런트는 온보딩으로 이동) |
| 500 | `SERVER_001` | 서버 내부 오류 |

## 5. Policy Service API

카테고리 코드: `MONTHLY_RENT`(월세), `JEONSE`(전세), `PURCHASE`(청약·구입), `PUBLIC_RENT`(공공임대), `OTHER`(기타 주거). 신청 기간 코드: `SPECIFIC_PERIOD`, `ALWAYS`, `CLOSED`.

목록 필터(POL-01·03): 삭제되지 않고 활성인 정책 중 `CLOSED`·신청 시작 전·마감일 경과 정책을 뺍니다. 시작일·마감일 당일은 포함하고, null 날짜는 그 경계 제한이 없습니다. 정렬 `DEADLINE`은 마감일 오름차순(null 마지막) → 정책 ID, `VIEW_COUNT`는 조회수 내림차순 → 정책 ID, `NAME`은 정책명 오름차순 → 정책 ID입니다.

<a id="pol-01"></a>
### POL-01 비로그인 정책 목록

`GET /api/policies/housing` · 비로그인

신청 가능한 주거 정책을 카테고리·정렬·페이지로 조회하는 공개 목록입니다. 회원 판정과 관심 여부는 없고, 로그인 토큰을 보내도 `favorite_yn`은 추가되지 않습니다. 결과가 없어도 200이며 `policies`는 `[]`입니다.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `category` | String(Enum) | 선택 | 단일 카테고리, 미지정 시 전체, 대소문자 구분 |
| Query | `sort` | String(Enum) | 선택 | `DEADLINE`(기본) / `VIEW_COUNT` / `NAME` |
| Query | `page` | Integer | 선택 | 기본 0, 0 이상 |
| Query | `size` | Integer | 선택 | 기본 8, 1~50 |

| 응답 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `policies` | Array<Object> | 불가 | 정책 목록, 없으면 `[]` |
| `page`, `size`, `totalPages` | Integer | 불가 | 페이지 정보 |
| `totalElements` | Long | 불가 | 필터에 맞는 전체 정책 수 |
| `hasNext` | Boolean | 불가 | 다음 페이지 여부 |
| `policies[].policy_id`, `policy_name` | String | 불가 | 정책 ID·정책명 |
| `policies[].category_codes` | Array<String(Enum)> | 불가 | 배열 순서를 표시 우선순위로 가정하지 않음 |
| `policies[].category_names` | Array<String> | 불가 | enum 이름순 정렬 후 변환한 표시명 |
| `policies[].regions` | Array<Object> | 불가 | 활성 대상 지역 전체, `[]`면 전국 |
| `policies[].regions[].region_code`, `region_name` | String | 불가 | 지역 코드·시도 포함 지역명 |
| `policies[].nationwide` | Boolean | 불가 | 대상 지역이 비어 있는지 |
| `policies[].region_summary` | String | 불가 | 전국 / 단일 지역명 / 첫 지역명 외 N곳 |
| `policies[].apply_end_date` | String(date) | 가능 | 신청 마감일 |
| `policies[].apply_period_code` | String(Enum) | 불가 | 목록에서는 CLOSED 제외 |
| `policies[].d_day` | Integer | 가능 | 한국 기준 마감까지 남은 일수, 당일 0, 마감일 없으면 null |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | 쿼리 enum·숫자·Boolean 변환 실패, page < 0, size < 1 또는 > 50 |
| 401 | `COMMON_002` | 공개 API에 잘못된 JWT를 함께 전달 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-02"></a>
### POL-02 정책 상세(로그인 시 자격 판정)

`GET /api/policies/{policyId}` · 로그인 선택 · F4·F6

정책 원문·요약·신청 정보와 추가 자격에 나온 용어를 조회합니다. 로그인 상태면 F4 관심 여부와 F6 회원 조건으로 나이·지역·소득·취업·무주택 판정을 더합니다. 상세 요청에서 Recommendation이나 OpenAI를 부르지 않습니다.

- 종합 판정: 하나라도 `DISABLE`이면 `DISABLE`, 그다음 `UNKNOWN`이 있으면 `UNKNOWN`, 나머지는 `ABLE`.
- `PolicyRepository.findById`만 써서 목록·F5와 달리 삭제·비활성·신청 기간을 따로 거르지 않습니다. 행이 없을 때만 `POLICY_001`입니다.
- User 장애·회로 열림이면 200으로 상세를 주고 `overallStatus=null`, `conditions=[]`입니다. 하트는 User와 따로 F4 결과를 씁니다.
- Engagement 장애면 200을 유지하고 `isFavorite=false`입니다. 비로그인도 같은 DTO이며 개인화 필드만 false/null/[]입니다.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Path | `policyId` | String | 필수 | 정책 ID. 숫자처럼 보여도 문자열, 별도 형식 검증 없음 |
| Header | `Authorization` | String | 선택 | 없으면 비로그인, 잘못된 토큰은 401 |

| 응답 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `policyId`, `policyName` | String | 불가 | 정책 ID·정책명 |
| `categories` | Array<String(Enum)> | 불가 | 카테고리 코드 |
| `categoryLabels` | Array<String> | 불가 | 한글 표시명, enum 이름순 |
| `apiSubCategory` | String | 가능 | 온통청년 중분류 원문 |
| `keywords` | String | 가능 | 원문 키워드 문자열(배열 아님) |
| `description`, `supportContent`, `extraQualification` | String | 가능 | 요약·지원 내용·추가 자격 |
| `terms` | Array<Object> | 불가 | extraQualification에 이름이 들어간 활성 용어, 없으면 `[]` |
| `terms[].termId` / `terms[].termName` | Long / String | 불가 | 용어 ID·용어명 |
| `applyPeriod` | String(Enum) | 불가 | 신청 기간 코드(키 이름이 `applyPeriodCode`가 아님) |
| `applyPeriodLabel` | String | 가능 | 기간 표시명 |
| `applyStartDate`, `applyEndDate` | String(date) | 가능 | 신청 시작일·마감일 |
| `applyMethod`, `applyUrl`, `refUrl` | String | 가능 | 신청 방법·신청 URL·참고 URL |
| `activeYn` | Boolean | 불가 | 정책 활성 상태 |
| `isFavorite` | Boolean | 불가 | 관심 여부. 비로그인·F4 장애는 false |
| `overallStatus` | String(Enum) | 가능 | `ABLE`/`DISABLE`/`UNKNOWN`. 비로그인·User 장애는 null |
| `conditions` | Array<Object> | 불가 | 정상 회원은 5개, 비로그인·User 장애는 `[]` |
| `conditions[].type` | String(Enum) | 불가 | `AGE`/`REGION`/`INCOME`/`EMPLOYMENT`/`HOUSELESS` |
| `conditions[].status` | String(Enum) | 불가 | `ABLE`/`DISABLE`/`UNKNOWN` |
| `conditions[].policyCondition` | String | 불가 | 판정에 쓴 정책 조건 문구 |
| `conditions[].memberValue` | String | 가능 | 회원 조건 문구. User가 지역명을 null로 주면 null |

| HTTP | code | 발생 조건 |
|---|---|---|
| 404 | `POLICY_001` | 해당 policyId 행이 없음 |
| 401 | `COMMON_002` | 유효하지 않거나 로그아웃된 토큰, 잘못된 회원 헤더 |
| 404 | `MEMBER_001` | User가 회원 없음·탈퇴로 응답 |
| 404 | `PROFILE_001` | User가 조건 미등록·삭제로 응답 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-03"></a>
### POL-03 내 조건 정책 목록

`GET /api/policies/housing/me` · 로그인 · F4·F6

F6로 회원 조건을 읽어 주거 정책 목록과 F4 관심 여부를 돌려줍니다. 필터·정렬은 POL-01과 같습니다.

- `onlyEligible=false`가 기본이라 로그인만으로 자격 필터가 걸리지 않습니다.
- `onlyEligible=true`면 나이·무주택·취업·지역(전국 또는 시군구 정확 일치)으로 제한합니다. 소득은 검사하지 않아 상세의 종합 `ABLE`과 같지 않으며, 무주택 UNKNOWN·취업 조건 없음 등도 일부 통과할 수 있습니다.
- User 장애·회로 열림이면 200을 유지하고 비회원 목록 조건으로 대체합니다. 응답은 회원 목록 DTO(`favorite_yn` 포함)를 유지합니다.
- F4는 정책 ID를 20개씩 나눠 호출하고, 어느 묶음이 실패해도 목록은 주고 하트는 전부 false입니다.

Request: Header `Authorization` 필수, Query `category`·`sort`·`page`·`size`(POL-01과 같음), `onlyEligible`(Boolean, 선택, 기본 false).

응답: POL-01과 같은 필드에 `policies[].favorite_yn`(Boolean, null 불가, F4 장애 시 false)이 더해집니다.

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | 쿼리 변환 실패, page < 0, size < 1 또는 > 50 |
| 401 | `COMMON_002` | 토큰 누락·유효하지 않음·로그아웃, 잘못된 회원 헤더 |
| 404 | `MEMBER_001` | User가 회원 없음·탈퇴로 응답 |
| 404 | `PROFILE_001` | User가 조건 미등록·삭제로 응답 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-04"></a>
### POL-04 비로그인 홈 카드뉴스

`GET /api/policies/card-news/guest` · 비로그인

대표 카드(cardNo=1)가 있는 정책을 최대 4건 돌려줍니다. 정책별 카드 4장이 아니라 정책별 대표 정보 1개입니다.

- 카드·정책 미삭제, 정책 활성, CLOSED 아님, 마감일 미경과. 일반 목록과 달리 신청 시작 전은 빼지 않습니다.
- 마감일 오름차순(null 마지막) → 정책 ID. 조회수 정렬은 없습니다. 입력(페이징·카테고리)은 없습니다.

| 응답 필드 (`result`는 배열) | 타입 | Null | 설명 |
|---|---|---|---|
| `[]` | Array<Object> | 불가 | 최대 4개, 없으면 `[]` |
| `[].policyId`, `[].policyName` | String | 불가 | 정책 ID·정책명 |
| `[].description` | String | 가능 | 정책 요약 설명(카드 body와 다름) |
| `[].applyEndDate` | String(date) | 가능 | 마감일 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | 공개 API에 잘못된 JWT를 함께 전달 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-05"></a>
### POL-05 로그인 회원 홈 카드뉴스

`GET /api/policies/card-news` · 로그인 · F6

F6 회원 거주지 기준으로 전국 정책 또는 거주 시군구·같은 시도 `xx000` 코드에 해당하는 대표 카드뉴스를 최대 4건 돌려줍니다. 나이·소득 등의 종합 판정으로 고르지 않습니다.

- 카드·정책 미삭제, 활성, CLOSED 아님, 마감일 미경과. 신청 시작일은 검사하지 않습니다.
- 마감일 오름차순(null 마지막) → 조회수 내림차순 → 정책명 → 정책 ID.
- User 장애·회로 열림이면 200으로 [POL-04](#pol-04) 결과를 대신 돌려줍니다. 회원 없음·조건 없음은 404를 유지합니다.
- `isFavorite`·판정 필드가 없고 F4도 부르지 않습니다.

Request: Header `Authorization` 필수. 응답: [POL-04](#pol-04)와 같은 배열 형식.

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | 토큰 누락·유효하지 않음·로그아웃, 잘못된 회원 헤더 |
| 404 | `MEMBER_001` | User가 회원 없음·탈퇴로 응답 |
| 404 | `PROFILE_001` | User가 조건 미등록·삭제로 응답 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-06"></a>
### POL-06 카드뉴스 상세

`GET /api/policies/card-detail/{policyId}` · 로그인 선택 · F4

선택한 정책의 활성 카드뉴스를 cardNo 오름차순으로 돌려줍니다. 비로그인은 하트 false, 로그인은 F4 관심 여부를 줍니다. F6 자격 판정은 하지 않으므로 이 API에는 `MEMBER_001`·`PROFILE_001`이 없습니다.

- 카드·정책 미삭제와 정책 활성만 검사합니다. 신청 기간·CLOSED는 검사하지 않아 마감 정책도 조회되며 `dDay`가 음수일 수 있습니다.
- 2번 카드의 `badges`에 연령 조건과 대상 지역 요약 2개를 넣고, 나머지 카드는 `[]`입니다.
- F4 실패는 오류가 아니라 `isFavorite=false`입니다.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Path | `policyId` | String | 필수 | 정책 ID(문자열) |
| Header | `Authorization` | String | 선택 | 없으면 비로그인, 잘못된 토큰은 401 |

| 응답 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `policyId` | String | 불가 | 정책 ID |
| `categoryLabel` | String | 가능 | enum 선언 순서로 정렬한 첫 카테고리의 한글명. 카테고리가 없으면 null |
| `dDay` | Integer | 가능 | 한국 날짜 기준 남은 일수. 마감일 없으면 null, 지났으면 음수 |
| `isAuthenticated` | Boolean | 불가 | 유효한 회원 ID가 전달됐는지(프로필 존재 확인 아님) |
| `isFavorite` | Boolean | 불가 | F4 관심 여부. 비로그인·F4 장애는 false |
| `applyUrl` | String | 가능 | 신청 URL |
| `cards` | Array<Object> | 불가 | 저장된 카드. 보통 4장이지만 누락 카드를 만들거나 보충하지 않음 |
| `cards[].cardNewsId` / `cards[].cardNo` | Long | 불가 | 카드뉴스 ID / 1~4 표시 순서 |
| `cards[].title` | String | 가능 | 카드 제목. 2번 카드 등은 null 가능 |
| `cards[].badges` | Array<String> | 불가 | 2번 카드만 연령·지역 배지, 그 외 `[]` |
| `cards[].body` | String | 불가 | 카드 본문(저장 상한 500자) |

| HTTP | code | 발생 조건 |
|---|---|---|
| 404 | `CARD_NEWS_001` | 미삭제·활성 정책의 조회 가능한 카드가 한 장도 없음(정책 없음·삭제·비활성 포함) |
| 401 | `COMMON_002` | 유효하지 않거나 로그아웃된 토큰, 잘못된 회원 헤더 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-07"></a>
### POL-07 지역 목록

`GET /api/regions` · 비로그인

거주지 선택에 쓸 시·도와 하위 시·군·구를 한 번에 돌려줍니다. 코드순 정렬 후 앞 2자리로 시·도를 묶고, 이름에서 시·도 접두어를 떼어 시군구명을 만듭니다. 하위 구가 있는 상위 시(예: 경기도 수원시)는 뺍니다. 반환 건수는 DB 상태에 따라 달라지므로 고정 계약이 아닙니다. `findAll` 후 상위 시만 거르며 `deletedAt` 필터는 따로 없습니다. 입력은 없습니다.

| 응답 필드 (`result`는 배열) | 타입 | Null | 설명 |
|---|---|---|---|
| `[]` | Array<Object> | 불가 | 시·도 목록, 없으면 `[]` |
| `[].sidoCode` / `[].sidoName` | String | 불가 | 코드 앞 2자리 / 시·도명 |
| `[].sigungu` | Array<Object> | 불가 | 코드순 하위 시·군·구(키 이름 `sigungu`) |
| `[].sigungu[].regionCode` | String | 불가 | 프로필 저장에 쓰는 5자리 코드 |
| `[].sigungu[].sigunguName` | String | 불가 | 시·도 접두어를 뺀 이름(세종은 세종특별자치시 그대로) |

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | 공개 API에 잘못된 JWT를 함께 전달 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-08"></a>
### POL-08 용어 목록

`GET /api/terms` · 비로그인

삭제되지 않은 용어를 termId 오름차순으로 전부 돌려줍니다. 검색·페이징은 없습니다. 비어 있어도 200 / `result: []`이며 `TERM_001`이 아닙니다.

| 응답 필드 (`result`는 배열) | 타입 | Null | 설명 |
|---|---|---|---|
| `[].termId` | Long | 불가 | 용어 ID |
| `[].term` | String | 불가 | 용어명(상세의 `termName`과 키가 다름) |
| `[].easyDescription` | String | 불가 | 쉬운 설명(상세의 `description`과 같은 원본) |
| `[].example` | String | 가능 | 예시 문장 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | 공개 API에 잘못된 JWT를 함께 전달 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-09"></a>
### POL-09 용어 상세

`GET /api/terms/{termId}` · 비로그인

미삭제 용어 하나의 이름과 쉬운 설명을 돌려줍니다. 목록과 달리 `example`은 없습니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Path | `termId` | Long | 필수 | 양수 검증은 없어 0·음수도 조회 후 없으면 404 |
| 응답 | `termId` | Long | 불가 | 용어 ID |
| 응답 | `termName` | String | 불가 | 용어명 |
| 응답 | `description` | String | 불가 | 쉬운 설명(`easyDescription` 원본) |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | termId가 Long으로 변환되지 않음 |
| 404 | `TERM_001` | 용어가 없거나 삭제됨 |
| 401 | `COMMON_002` | 공개 API에 잘못된 JWT를 함께 전달 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-10"></a>
### POL-10 정책 수집·동기화

`POST /api/policies/sync` · ADMIN

온통청년을 페이지당 100건, 최대 50페이지까지 돌며 승인된 주거 정책을 모으고, AI 분석 후 정책·지역·카드뉴스·Outbox를 저장합니다. 전체 수집은 약 30분 걸리며 응답까지 동기 처리합니다.

- 주거 대분류 포함·정책 ID 존재·승인 코드 조건을 확인합니다. 정책마다 독립 트랜잭션이라 한 건의 분석·저장·Outbox 기록이 실패하면 그 건만 롤백하고 다음 정책을 계속합니다. 기존 카드는 중복 생성하지 않습니다.
- 페이지 요청이 실패하면 한 번 더 시도합니다. 두 번 모두 실패하거나 응답 자체·응답의 `result`가 null이면 수집을 멈추고 502 `POLICY_002`를 돌려줍니다. 앞서 저장된 정책은 유지합니다.
- 응답은 정상인데 정책 목록(`youthPolicyList`)이 null이거나 비어 있으면 오류가 아니라 마지막 페이지로 보고 순회를 끝낸 뒤 200을 돌려줍니다.
- 개별 실패가 있어도 전체 순회를 마치면 200이며, 성공 건수는 신규 건수가 아니라 정상 처리 항목 수입니다. 실패 목록은 응답에 없습니다.
- Kafka 전송은 커밋 후 Relay가 비동기로 하므로, 이 응답과 Engagement 반영 완료 시점은 다릅니다. Gateway가 ADMIN 전용으로 막고 Policy 컨트롤러도 역할을 다시 확인합니다.

Request: Header `Authorization` 필수. Query·Path·Body는 없습니다.

응답: `result` 자체가 String입니다. 예 `"온통청년 주거 정책 3건 동기화가 완료되었습니다."`

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | 토큰 누락·유효하지 않음·로그아웃, 잘못된 회원 헤더 |
| 403 | `COMMON_004` | JWT 역할이 ADMIN이 아님 |
| 502 | `POLICY_002` | 페이지 요청 재시도 실패, 또는 응답·응답 `result`가 null. `result`에 `stoppedPage`·`savedCount` (정책 목록만 비면 200) |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-11"></a>
### POL-11 정책 이벤트 발행 상태 조회

`GET /api/policies/admin/events` · ADMIN

관리자 화면 '이벤트 동기화' 탭이 부릅니다. `policy_outbox`에서 Kafka 발행 대기 건수, 발행 건수, 마지막 발행 시각, 최근 이벤트 10건을 돌려줍니다. payload는 크기가 커서 넣지 않습니다. `publishedCount`는 Outbox 발행 완료 기록 수이며 Engagement 반영·중복·DLT 건수가 아닙니다(그 값은 Prometheus 지표). Gateway는 로그인만 검사하고 Policy 컨트롤러가 ADMIN을 검사합니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Header | `Authorization` | String | 필수 | 관리자 Bearer 토큰 |
| Header | `X-Member-Role` | String | Gateway가 넣음 | ADMIN이 아니면 403 |
| 응답 | `pendingCount` | Long | 아님 | 아직 보내지 않은 이벤트 수 |
| 응답 | `publishedCount` | Long | 아님 | 보낸 이벤트 수 |
| 응답 | `lastPublishedAt` | DateTime | 가능 | 마지막 발행 시각, 보낸 적 없으면 null |
| 응답 | `recentEvents` | Array | 아님 | 최근 10건, 최신순 |
| 응답 | `recentEvents[].eventId` | String | 아님 | 이벤트 ID(UUID) |
| 응답 | `recentEvents[].policyId` | String | 아님 | 바뀐 정책 ID(Kafka key) |
| 응답 | `recentEvents[].eventType` | String | 아님 | `policy-updated` |
| 응답 | `recentEvents[].createdAt` | DateTime | 아님 | Outbox 기록 시각 |
| 응답 | `recentEvents[].publishedAt` | DateTime | 가능 | 발행 시각, 대기 중이면 null |

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | 토큰 없이 호출(Gateway에서 막음) |
| 403 | `COMMON_004` | 로그인했지만 ADMIN이 아님 |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-12"></a>
### POL-12 정책 후보 조회 (내부, F2)

`POST /internal/policies/candidates` · 내부 · recommendation이 호출

카테고리·거주 지역·판정 날짜에 맞는 정책 후보를 최대 20건 돌려줍니다. 회원 ID·검색 문장·정렬·limit는 받지 않으며, 정렬과 최대 20건은 Policy가 고정합니다. 원본 `Hyejabonjuui/backend` `3e47bb1`의 `searchActivePolicies` 기준이며 Recommendation 담당자와 합의했습니다(Issue #53, 2026-11-02).

- 미삭제·활성·CLOSED 아님·시작일 이전 아님·마감일 경과 아님을 요청 `today`로 판단합니다. 시작일·마감일 당일 포함, null 날짜는 제한 없음.
- 카테고리는 OR이고 빈 배열은 전체입니다. 전국·정확 시군구·기존 `xx000` 같은 시도 코드를 지원합니다.
- 마감일 오름차순(null 마지막) → 정책 ID로 최대 20건을 먼저 정합니다. 나이·소득·취업·무주택으로 미리 빼지 않으며, 자격 판정과 F3 관심 조회는 Recommendation 책임입니다.
- 후보가 없으면 200 / `result: []`이며, 장애를 빈 목록으로 바꾸지 않습니다. 원본 null·enum 값을 그대로 보존합니다.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Header | `Content-Type` | String | 필수 | `application/json` |
| Body | `categories` | Array<String(Enum)> | 필수 | null·원소 null 불가, `[]`는 전체. 반복 카테고리로 결과가 중복되지 않음 |
| Body | `regionCode` | String | 필수 | 숫자 5자리. 지역 테이블 존재 확인은 하지 않음 |
| Body | `today` | String(date) | 필수 | 한국 기준 날짜. Recommendation 판정 날짜와 같아야 하며 Policy 서버 날짜로 덮어쓰지 않음 |

```json
{ "categories": ["MONTHLY_RENT", "JEONSE"], "regionCode": "11440", "today": "2026-11-02" }
```

| 응답 필드 (`result`는 배열) | 타입 | Null | 설명 |
|---|---|---|---|
| `[].policyId`, `[].policyName` | String | 불가 | 정책 ID·정책명 |
| `[].categories` | Array<String(Enum)> | 불가 | 카테고리 코드 |
| `[].applyPeriodCode` | String(Enum) | 불가 | `SPECIFIC_PERIOD` / `ALWAYS`(CLOSED는 후보 제외) |
| `[].applyStartDate`, `[].applyEndDate` | String(date) | 가능 | 시작일·마감일(null 마감도 후보, 마지막 정렬) |
| `[].ageLimitYn` | Boolean | 불가 | 나이 제한 여부 |
| `[].minAge`, `[].maxAge` | Integer | 가능 | 최소·최대 나이 |
| `[].incomeConditionCode` | String(Enum) | 가능 | `NO_RESTRICTION` / `COMPARABLE` / `CONDITIONAL` / `UNKNOWN` |
| `[].incomeMin`, `[].incomeMax` | Integer | 가능 | 개인 연소득 최솟값·최댓값(원) |
| `[].incomeEtc` | String | 가능 | 복합 소득 조건 원문 |
| `[].employmentCodes` | Array<String(Enum)> | 가능 | 정책 취업 조건 코드, 원본 null 유지. 배열 순서를 판정에 쓰지 않음 |
| `[].houselessRequirement` | String(Enum) | 불가 | `REQUIRED` / `NOT_REQUIRED` / `UNKNOWN` |
| `[].extraQualification` | String | 가능 | 추가 자격 원문 |
| `[].regions` | Array<Object> | 불가 | 매칭 지역만이 아닌 정책의 전체 활성 대상 지역(코드순), `[]`는 전국. 삭제된 연결·지역 제외 |
| `[].regions[].regionCode` | String | 불가 | 대상 지역 코드 |
| `[].regions[].sigunguName` | String | 불가 | 시·도를 포함한 전체 지역명(POL-07의 같은 이름 필드와 형식이 다름) |

`employmentCodes` 값: `EMPLOYED` / `SELF_EMPLOYED` / `UNEMPLOYED` / `FREELANCER` / `DAILY_WORKER` / `ENTREPRENEUR` / `SHORT_TERM_WORKER` / `FARMER` / `OTHER` / `NO_RESTRICTION`.

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | JSON 본문 없음·파싱 실패, enum·날짜 형식 오류 |
| 400 | `COMMON_003` | 필수 필드 누락·null, categories 원소 null, regionCode 숫자 5자리 검증 실패 |
| 415 | `COMMON_008` | 지원하지 않는 Content-Type |
| 500 | `SERVER_001` | Policy 내부 오류 |

<a id="pol-13"></a>
### POL-13 관심 등록용 정책 스냅샷 조회 (내부, F5)

`GET /internal/policies/{policyId}` · 내부 · engagement가 호출

관심 등록 때 정책 원본의 유효성을 확인하고 Engagement 로컬 스냅샷 생성·갱신에 필요한 값을 줍니다. 원본 `3e47bb1`의 `FavoriteService.createFavorite` 기준이며 Engagement 담당자와 합의했습니다(Issue #60, 2026-11-03).

- 정책 존재·미삭제·`activeYn=true`만 확인합니다. 신청 시작 전·마감일 경과·CLOSED도 이 조건을 만족하면 성공합니다. 목록이나 F2의 기간 필터를 적용하지 않습니다(기존 관심 등록도 기간을 검사하지 않았음).
- 요청 본문·회원 ID·관심 ID는 없습니다. `favoriteId`·관심 등록일은 Engagement 소유라 응답하지 않습니다.
- 이 GET은 관심을 저장하지 않습니다. 관심 삭제에는 쓰지 않습니다(등록 전용).
- 내부 처리 오류는 500 `SERVER_001`이며 정상 빈 결과나 정책 없음으로 바꾸지 않습니다. Engagement가 장애를 503 `SERVER_002`로 바꾸는 것([ENG-02](#eng-02))과 구분합니다.

| 위치 | 이름 | 타입 | 필수 / Null | 설명 |
|---|---|---|---|---|
| Path | `policyId` | String | 필수 | 정책 ID(문자열) |
| 응답 | `policyId`, `policyName` | String | 불가 | 정책 ID·정책명 |
| 응답 | `categories` | Array<String(Enum)> | 불가 | 카테고리 코드, 순서 의미 없음. 표시명은 Engagement가 만듦 |
| 응답 | `supportContent` | String | 가능 | 지원 내용 원문 |
| 응답 | `applyEndDate` | String(date) | 가능 | 마감일 |
| 응답 | `applyPeriodCode` | String(Enum) | 불가 | `SPECIFIC_PERIOD` / `ALWAYS` / `CLOSED` |
| 응답 | `applyUrl` | String | 가능 | 신청 URL |
| 응답 | `activeYn` | Boolean | 불가 | 성공 시 true |
| 응답 | `deleted` | Boolean | 불가 | 소프트 삭제 여부, 성공 시 false |

```json
{
  "isSuccess": true,
  "code": "SUCCESS_001",
  "message": "성공입니다.",
  "result": {
    "policyId": "20261030005400214653",
    "policyName": "전세보증금반환보증 보증료 지원",
    "categories": ["JEONSE"],
    "supportContent": "실 납부 전세보증금반환보증 보증료(최대 40만원) 지원",
    "applyEndDate": null,
    "applyPeriodCode": "ALWAYS",
    "applyUrl": null,
    "activeYn": true,
    "deleted": false
  }
}
```

| HTTP | code | 발생 조건 |
|---|---|---|
| 404 | `POLICY_001` | 정책 없음·삭제·비활성 |
| 500 | `SERVER_001` | Policy 내부 오류 |

## 6. Engagement Service API

회원은 `X-Gateway-Assertion` 내부 JWT의 `sub`로 식별하고 `X-Member-Id` 헤더는 무시합니다. 회원 존재·탈퇴 여부는 확인하지 않습니다. 정책 정보는 engagement DB의 `policy_snapshot` 읽기 모델(F5와 `policy-updated`로 갱신)을 씁니다. 응답 필드는 snake_case이고 페이지 정보만 camelCase입니다.

FavoriteItem (ENG-01 목록 원소, ENG-02 응답):

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `favorite_id` | Long | 아님 | 관심 정책 ID |
| `policy_id`, `policy_name` | String | 아님 | 정책 ID·정책명 |
| `category_codes` | Array<String> | 아님 | 분류 enum 목록, 빈 배열 가능. 순서 보장 없음 |
| `category_names` | Array<String> | 아님 | 분류 한글명. `category_codes`와 배열 인덱스로 짝짓지 않음 |
| `support_content` | String | 가능 | 지원 내용 |
| `apply_end_date` | String(date) | 가능 | 신청 마감일 |
| `apply_period_code` | String(enum) | 가능 | `SPECIFIC_PERIOD` / `ALWAYS` / `CLOSED` |
| `apply_url` | String | 가능 | 신청 URL |
| `created_at` | String(date-time) | 아님 | 관심 등록 시각 |

NotificationItem (ENG-04 목록 원소, ENG-05 응답):

| 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `notification_id` | Long | 아님 | 알림 ID |
| `member_id` | Long | 아님 | 알림 소유 회원 ID |
| `policy_id`, `policy_name` | String | 아님 | 관련 정책 ID·정책명 |
| `content` | String | 아님 | 고정 문구 "관심 정책의 신청 마감이 일주일 남았어요" |
| `read_yn` | Boolean | 아님 | 읽음 여부 |
| `apply_end_date` | String(date) | 아님 | 알림 생성 때 저장한 마감일 스냅샷 |
| `created_at` | String(date-time) | 아님 | 알림 생성 시각 |

<a id="eng-01"></a>
### ENG-01 관심 정책 목록

`GET /api/favorite` · 로그인

본인의 삭제되지 않은 관심 정책을 최근 등록순(같으면 관심 정책 ID 내림차순)으로 조회합니다. 정책명 또는 지원 내용으로 대소문자를 구분하지 않는 부분 검색을 하며, 앞뒤 공백을 지우고 생략·빈 문자열·공백만이면 전체를 조회합니다. `%`, `_`는 와일드카드가 아닌 문자로 검색합니다. 없으면 빈 배열입니다.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `keyword` | String | 선택 | 정책명·지원 내용 검색어 |
| Query | `page` | Integer | 선택 | 0 이상, 기본 0 |
| Query | `size` | Integer | 선택 | 1 이상, 기본 8, 최대값 제한 없음 |

| 응답 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `favorites` | Array<FavoriteItem> | 아님 | 현재 페이지 관심 정책 |
| `page`, `size`, `totalPages` | Integer | 아님 | 페이지 정보 |
| `totalElements` | Long | 아님 | 검색 조건에 맞는 전체 관심 정책 수 |
| `hasNext` | Boolean | 아님 | 다음 페이지 여부 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | page < 0, size ≤ 0, 정수로 바꿀 수 없는 Query |
| 401 | `COMMON_002` | 인증 누락·실패 |

<a id="eng-02"></a>
### ENG-02 관심 정책 등록

`POST /api/favorite/{policyId}` · 로그인 · F5

[POL-13](#pol-13)(F5)로 정책 스냅샷을 조회한 뒤 로컬 정책 읽기 모델을 갱신하고 관심 정책을 등록합니다. 순서는 F5 호출 → 스냅샷 생성·갱신 → 중복 확인 → 관심 저장이며, F5 이후의 스냅샷 반영과 관심 저장은 같은 로컬 트랜잭션입니다. 같은 회원·정책의 중복 등록은 허용하지 않고 스냅샷 변경도 롤백됩니다. 신청 기간은 검사하지 않아 마감·CLOSED 정책도 F5가 성공하면 등록됩니다. 정책 서비스 장애 시 기존 로컬 스냅샷으로 대신 등록하지 않습니다.

- Request: Path `policyId`(String, 필수)
- 응답: `result`는 FavoriteItem 객체

| HTTP | code | 발생 조건 |
|---|---|---|
| 401 | `COMMON_002` | 인증 누락·실패 |
| 404 | `POLICY_001` | F5가 404 / `POLICY_001`(정책 없음·유효하지 않음)을 응답 |
| 409 | `FAVORITE_001` | 이미 등록한 정책, 같은 회원·정책 중복 저장 충돌 |
| 503 | `SERVER_002` | F5 통신 실패, 회로 차단, 공통 응답 형식 불일치, 정책 ID 불일치, 정책명·분류 누락, `activeYn≠true` 또는 `deleted≠false` 등 |

F5 호출이 중복 확인보다 먼저이므로, 이미 등록한 정책이라도 Policy 장애면 409보다 503이 먼저 나올 수 있습니다.

<a id="eng-03"></a>
### ENG-03 관심 정책 해제

`DELETE /api/favorite/{policyId}` · 로그인

본인의 관심 정책을 DB에서 영구 삭제합니다. `policyId`는 favorite_id가 아닌 정책 ID입니다. 로컬 정책 존재 여부를 먼저 확인하며 F5는 부르지 않습니다. 같은 정책을 다시 해제하면 `FAVORITE_002`입니다. 이미 생성된 알림은 함께 지우지 않습니다.

- Request: Path `policyId`(String, 필수, 공백 불가)
- 응답: `result`는 `null`

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | policyId가 공백이라 검증 실패 |
| 401 | `COMMON_002` | 인증 누락·실패 |
| 404 | `POLICY_001` | 로컬에 정책 ID가 없음 |
| 404 | `FAVORITE_002` | 본인이 등록한 삭제되지 않은 관심 정책이 없음 |

<a id="eng-04"></a>
### ENG-04 알림 목록

`GET /api/notification` · 로그인

본인의 삭제되지 않은 알림을 생성 시각 내림차순(같으면 알림 ID 내림차순)으로 조회합니다. 조회만으로 읽음 처리하지 않습니다. `unread_count`는 현재 페이지와 무관한 본인 전체 미열람 건수이며, 알림이 없으면 빈 배열과 `unread_count=0`입니다.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Query | `page` | Integer | 선택 | 0 이상, 기본 0 |
| Query | `size` | Integer | 선택 | 1 이상, 기본 8, 최대값 제한 없음 |

| 응답 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `notifications` | Array<NotificationItem> | 아님 | 현재 페이지 알림 |
| `unread_count` | Long | 아님 | 본인의 전체 미열람 알림 수 |
| `page`, `size`, `totalPages` | Integer | 아님 | 페이지 정보 |
| `totalElements` | Long | 아님 | 본인의 삭제되지 않은 전체 알림 수 |
| `hasNext` | Boolean | 아님 | 다음 페이지 여부 |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | page < 0, size ≤ 0, 정수로 바꿀 수 없는 Query |
| 401 | `COMMON_002` | 인증 누락·실패 |

<a id="eng-05"></a>
### ENG-05 알림 읽음 처리

`PATCH /api/notification/{notificationId}/read` · 로그인

본인의 삭제되지 않은 알림을 읽음으로 바꿉니다. 이미 읽은 알림을 다시 요청해도 성공하고 `read_yn=true`를 유지합니다. 다른 회원의 알림은 존재 여부를 구분하지 않고 404입니다.

- Request: Path `notificationId`(Long, 필수, 1 이상)
- 응답: `result`는 NotificationItem 객체(`read_yn=true`)

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | notificationId ≤ 0 또는 Long으로 바꿀 수 없음 |
| 401 | `COMMON_002` | 인증 누락·실패 |
| 404 | `NOTIFICATION_001` | 본인의 삭제되지 않은 알림이 없음 |

<a id="eng-06"></a>
### ENG-06 알림 삭제

`DELETE /api/notification/{notificationId}` · 로그인

본인의 삭제되지 않은 알림을 DB에서 영구 삭제합니다. 다른 회원의 알림이나 이미 삭제한 알림은 404입니다.

- Request: Path `notificationId`(Long, 필수, 1 이상)
- 응답: `result`는 `null`

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | notificationId ≤ 0 또는 Long으로 바꿀 수 없음 |
| 401 | `COMMON_002` | 인증 누락·실패 |
| 404 | `NOTIFICATION_001` | 본인의 삭제되지 않은 알림이 없음 |

<a id="eng-07"></a>
### ENG-07 관심 정책 ID 일괄 조회 (내부, F3·F4)

`POST /internal/favorites/active-policy-ids` · 내부 · recommendation(F3)·policy(F4)가 호출

요청 `policyIds`와 해당 회원의 삭제되지 않은 관심 정책 ID의 교집합을 돌려줍니다. 요청 순서를 유지하고 중복은 지웁니다. 등록되지 않은 ID는 빼며, 빈 배열이나 일치 없음은 빈 배열입니다. 정책의 `activeYn`·삭제 여부는 추가로 거르지 않습니다. engagement `SecurityConfig`가 `/internal/**`을 Gateway assertion 없이 허용합니다.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Body | `memberId` | Long | 필수 | 양수 회원 ID, 호출 서비스가 전달 |
| Body | `policyIds` | Array<String> | 필수 | 최대 20개, 빈 배열 허용, null 불가 |
| Body | `policyIds[]` | String | 필수 | null·빈 문자열 불가(`@NotEmpty`라 공백 문자열은 거부하지 않음) |
| 응답 | `result` 자체 | Array<String> | 아님 | 관심 등록한 정책 ID 목록, 객체로 감싸지 않음. 예 `["R202609230001"]` |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_003` | memberId 누락·0 이하, policyIds 누락·null·21개 이상, 원소 null·빈 문자열 |
| 400 | `COMMON_001` | Body 없음, JSON 구문 오류, 필드 타입 변환 실패 |
| 415 | `COMMON_008` | 지원하지 않는 Content-Type |

## 7. Recommendation Service API

<a id="rec-01"></a>
### REC-01 회원 맞춤 정책 검색(AI)

`GET /api/policies/search` · 로그인 · F1·F2·F3

로그인 회원의 조건과 검색어로 청년 주거 정책을 검색합니다. 나이·지역·소득·취업·무주택을 판정하고 AI로 검색 의도 분석과 판정 이유를 만든 뒤, 받을 수 있는 정책(`approved`)·추가 확인이 필요한 정책(`underReview`)·조건에 맞지 않는 정책(`declined`)으로 나눠 최대 20건을 돌려줍니다. `recommendation.search.enabled=true`(현재 `application.yml` 기본값)일 때만 열립니다. 요구사항 명세서의 `/api/recommendations`는 제안 경로였고 실제 경로는 이것입니다.

처리 순서(검사 순서가 오류 우선순위입니다):

1. `X-Member-Id`가 없으면 401 `COMMON_002`, 검색어가 비었거나 공백만이면 `POLICY_SEARCH_006`, 200자를 넘으면 `POLICY_SEARCH_007`.
2. F1([USR-11](#usr-11))로 회원 조건 조회. `MEMBER_001`·`PROFILE_001`은 그대로 전달, 그 밖의 실패는 500 `SERVER_001`.
3. 검색 의도: 지원 해시태그는 바로 카테고리로 바꾸고, 그 외는 AI 분석. AI가 실패하면 전체 카테고리로 검색. 주거와 무관하면 `POLICY_SEARCH_002`.
4. F2([POL-12](#pol-12))로 후보 최대 20건. 실패는 500 `SERVER_001`. 후보가 없으면 세 그룹 모두 빈 배열.
5. F3([ENG-07](#eng-07))로 관심 여부. 실패해도 검색은 성공하고 `isFavorite=false`.
6. 자격 판정 후 AI 판정 이유 생성. 실패하면 기본 문장 사용.

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| Header | `Authorization` | String | 필수 | Gateway가 검증하는 JWT |
| Header | `X-Member-Id` | Long | Gateway가 넣음 | 클라이언트가 직접 지정하지 않음 |
| Query | `query` | String | 필수 | 공백 불가, 최대 200자. 자연어 또는 지원 해시태그(예 `#월세`) |

| 응답 필드 | 타입 | Null | 설명 |
|---|---|---|---|
| `approvedCount`, `underReviewCount`, `declinedCount` | Integer | 아님 | 그룹별 개수 |
| `approved`, `underReview`, `declined` | Array | 아님 | 그룹별 정책 목록(원소 형식 동일) |
| `[].policyId`, `[].policyName` | String | 아님 | 정책 ID·이름 |
| `[].categories` | Array | 가능 | 주거 카테고리 목록 |
| `[].applyEndDate` | Date | 가능 | 신청 종료일 |
| `[].applyPeriod` | Enum | 가능 | 신청 기간 유형 |
| `[].isFavorite` | Boolean | 아님 | 관심 등록 여부 |
| `[].aiReason` | String | 아님 | AI 생성 또는 기본 판정 이유 |
| `[].status` | Object | 아님 | 5가지 판정 결과 |
| `[].status.age`, `region`, `income`, `employment`, `houseless` | Enum | 아님 | `ABLE` / `DISABLE` / `UNKNOWN` |

| HTTP | code | 발생 조건 |
|---|---|---|
| 400 | `COMMON_001` | 필수 query 파라미터 누락 |
| 400 | `POLICY_SEARCH_006` | 검색어가 비었거나 공백만 |
| 400 | `POLICY_SEARCH_007` | 검색어 200자 초과 |
| 400 | `POLICY_SEARCH_002` | 주거 정책과 무관한 검색어로 판별 |
| 401 | `COMMON_002` | 토큰 없음 또는 회원 인증 실패 |
| 404 | `MEMBER_001` | 회원 없음(F1) |
| 404 | `PROFILE_001` | 회원 조건 없음(F1) |
| 500 | `SERVER_001` | F1·F2 통신 실패 또는 서버 내부 오류 |

## 8. 내부 OpenFeign 통신 F1~F6

내부 경로는 `/internal/**`이고 Gateway에 노출하지 않습니다. 서비스 주소는 Eureka 서비스 이름으로 찾습니다. 기준: [MSA 아키텍처 설계서](../architecture/HYEJA_MSA_Architecture_Design.md) + [설계서와 코드 대조 결과](../architecture/설계_코드_대조.md).

| # | 호출 → 제공 | 엔드포인트 | 용도 | 장애 시 | 상태 |
|---|---|---|---|---|---|
| F1 | recommendation → user | [USR-11](#usr-11) | 회원의 내 조건 | 검색 실패 500 `SERVER_001`(`MEMBER_001`·`PROFILE_001`은 그대로) | 구현 |
| F2 | recommendation → policy | [POL-12](#pol-12) | 정책 후보 일괄 조회 | 검색 실패 500 `SERVER_001` | 구현 |
| F3 | recommendation → engagement | [ENG-07](#eng-07) | 관심 여부 일괄 조회 | 하트 없이 응답 | 구현 |
| F4 | policy → engagement | [ENG-07](#eng-07) | 관심 여부(목록·상세·카드뉴스), 20개씩 나눠 조회 | 하트 없이 응답 | 구현 |
| F5 | engagement → policy | [POL-13](#pol-13) | 관심 등록 때 정책 존재·스냅샷 | `POLICY_001`은 404 그대로, 그 밖은 503 `SERVER_002`, 저장 안 함 | 구현 |
| F6 | policy → user | [USR-11](#usr-11) | 회원 조건(맞춤 목록·상세 판정·회원 카드뉴스) | 연결 실패·시간 초과·5xx는 내부적으로 `SERVER_002`(policy), 세 화면 모두 비로그인 응답으로 대체. `MEMBER_001`·`PROFILE_001`은 그대로 | 구현 |

- **서킷 브레이커:** F1~F6 모두 호출별 독립 Resilience4j 회로(`recUserProfile`, `recPolicyCandidates`, `recEngagementFavorites`, `policyEngagementFavorites`, `policyUserProfile`, `engPolicySnapshot`). 최근 20회 중 최소 10회, 실패율 50% 이상이면 15초 OPEN, HALF_OPEN 시험 3회. 4xx(`MEMBER_001`·`PROFILE_001`·`POLICY_001` 포함)는 실패율에서 뺍니다. 회로가 열려도 응답은 위 표와 같고 Retry는 없습니다.
- **시간 제한:** policy·engagement의 Feign은 연결 1초, 응답 2초입니다.
- **인증:** `/internal/**`은 Gateway 외부 라우팅에 없고 내부 네트워크에서만 호출합니다. 별도 `X-Internal-Token`은 쓰지 않으며, 사용자 JWT·`X-Member-Id`를 내부 API 인증 수단으로 보지 않습니다. 내부 API 구현과 서비스 간 인증·네트워크 통제는 별개이며, 현재 통제는 라우팅 제외와 서비스 포트 비공개입니다.
- **규칙:** 한 요청 안에서 A→B→A로 되돌아오는 호출은 만들지 않습니다. 여러 건은 일괄 조회로 한 번에 가져옵니다.

두지 않는 호출 (#1 확정):

- **F7** (user → policy, 거주지 코드 검증·시군구 이름): user_db에 지역 읽기 복사본을 두고 policy와 같은 `region_sigungu.csv`로 기동 때 적재합니다.
- **F8** (policy → recommendation, 정책 상세 자격 판정): 판정 코드(`PolicyEligibilityEvaluator` 등)를 policy와 recommendation에 같은 내용으로 각각 둡니다(공유 모듈 아님). 판정 규칙의 기준은 recommendation이 갖습니다.

## 9. Kafka 이벤트 계약

| 토픽 | 발행 | 구독(그룹) | 발생 시점 | 목적 | 상태 |
|---|---|---|---|---|---|
| `policy-updated` | policy | engagement(`engagement-policy-snapshot`) | 수집·변경 저장 후 | 정책 스냅샷 읽기 모델 갱신 | 구현 |
| `policy-updated-dlt` | engagement | - | 재시도 후에도 실패, 깨진 JSON | 실패 메시지 보관, `hyeja_policy_updated_dlt_total` 집계 | 구현 |
| `member-withdrawn` | user | engagement(`engagement-member-cleanup`) | 탈퇴 커밋 후 | 관심 정책·알림 정리 | 구현 (#1 확정) |
| `member-withdrawn-dlt` | engagement | engagement(`engagement-member-withdrawn-dlt-store`) | 재시도 후에도 실패 | `failed_member_event` 저장, 누적 시 관리자 메일 | 구현 |
| `member-registered` | user | engagement | 가입 커밋 후 | 웰컴 메일 | 하지 않음 (#1 확정, 토픽만 생성) |
| `notification-created` | engagement | engagement 내부 Worker | 앱 알림 저장 후 | 마감 알림 메일 | 하지 않음 (토픽만 생성) |

공통 필드: `eventId`(중복 처리 확인), `eventType`, `eventVersion`, `occurredAt`(시간대 오프셋 포함 ISO-8601, REST의 오프셋 없는 시간 형식과 다름), `aggregateId`. 개인정보와 토큰은 넣지 않습니다. 공통 필드와 본문은 한 객체에 같이 담고 enum은 이름 그대로 보냅니다.

`member-withdrawn` (key = `memberId` 문자열):

```json
{
  "eventId": "0f8fad5b-d9cb-469f-a165-70867728950e",
  "eventType": "member-withdrawn",
  "eventVersion": 1,
  "occurredAt": "2026-11-03T17:30:00+09:00",
  "aggregateId": "1",
  "memberId": 1
}
```

`policy-updated` (key = `policyId`, 같은 정책의 이벤트는 순서대로 도착). 본문 `policyId, policyName, categories, supportContent, applyEndDate, applyPeriodCode, applyUrl, activeYn, deleted`는 engagement 관심 목록 응답과 키워드 검색(정책명·지원 내용)에 필요한 값입니다.

```json
{
  "eventId": "6f1c2a0e-3b5d-4c8e-9a7f-2d4e6b8c0a12",
  "eventType": "policy-updated",
  "eventVersion": 1,
  "occurredAt": "2026-11-03T14:00:00+09:00",
  "aggregateId": "20250101005400210001",
  "policyId": "20250101005400210001",
  "policyName": "청년 월세 지원",
  "categories": ["MONTHLY_RENT"],
  "supportContent": "월 최대 20만 원",
  "applyEndDate": "2026-12-31",
  "applyPeriodCode": "SPECIFIC_PERIOD",
  "applyUrl": "https://example.com/apply",
  "activeYn": true,
  "deleted": false
}
```

처리 규칙:

- **Outbox:** policy는 `policy_outbox`, user는 `member_outbox`에 데이터 변경과 같은 트랜잭션으로 기록합니다. Relay가 1초마다 미발행 행을 순서대로 보내고 성공한 행에 `published_at`을 남깁니다. 저장이 롤백되면 이벤트도 남지 않습니다. 발행 현황은 [POL-11](#pol-11)로 봅니다.
- **멱등성:** engagement는 처리한 `eventId`를 `processed_policy_event`·`processed_member_event`에 남기고 같은 이벤트가 다시 오면 건너뜁니다. 처리 이력과 데이터 변경은 한 트랜잭션이라 실패하면 이력도 롤백됩니다.
- **`policy-updated` 반영:** `policy_snapshot`을 넣거나 고치고 `deleted=true`면 삭제 표시합니다. 반영·중복 건수는 `hyeja_policy_updated_events_total{result="applied"|"duplicate"}`로 셉니다.
- **재시도·DLT:** 처리에 실패하면 1초 간격으로 3번 더 시도하고, 그래도 실패하거나 JSON이 깨졌으면 각 DLT로 보냅니다.
- **관리자 장애 알림:** `member-withdrawn-dlt` 메시지는 `failed_member_event`에 저장(같은 topic·partition·offset은 한 번만)하고 토픽별 누적을 셉니다. 누적이 `ADMIN_DLT_THRESHOLD`(기본 3)에 닿으면 `ADMIN_ALERT_EMAIL`로 메일을 보내고 누적을 0으로 되돌리며, 발송 실패 시 60초마다 다시 보냅니다.
- Saga 보상 트랜잭션은 구현하지 않았습니다. 탈퇴 정리는 이벤트 + 재시도·DLT의 최종 일관성으로 처리합니다.

## 10. 오류 코드

각 서비스 `ErrorStatus`에 정의된 값입니다(develop 2026-11-04). API별 발생 조건은 4~7장의 오류 표가 기준입니다.

| code | HTTP | message | 정의된 서비스 |
|---|---|---|---|
| `SUCCESS_001` | 200 | 성공입니다. | 전체 |
| `COMMON_001` | 400 | 잘못된 요청입니다. | 전체 |
| `COMMON_002` | 401 | 인증이 필요합니다. | 전체(Gateway 포함) |
| `COMMON_003` | 400 | 입력값을 확인해 주세요. | 전체 |
| `COMMON_004` | 403 | 접근 권한이 없습니다. | 전체(Gateway 포함) |
| `COMMON_005` | 404 | 요청한 대상을 찾을 수 없습니다. | 전체 |
| `COMMON_006` | 405 | 지원하지 않는 요청 메서드입니다. | 전체 |
| `COMMON_007` | 409 | 요청이 현재 상태와 충돌합니다. | 전체 |
| `COMMON_008` | 415 | 지원하지 않는 요청 형식입니다. | 전체 |
| `COMMON_009` | 406 | 요청한 응답 형식을 제공할 수 없습니다. | 전체 |
| `SERVER_001` | 500 | 서버 오류가 발생했습니다. | 전체 |
| `SERVER_002` | 503 | 회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요. | policy(user F6 호출 실패) |
| `SERVER_002` | 503 | 정책 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요. | engagement(policy F5 호출 실패) |
| `MEMBER_001` | 404 | 존재하지 않는 회원입니다. | user, policy, recommendation, engagement |
| `MEMBER_002` | 409 | 이미 가입된 이메일입니다. | user |
| `MEMBER_003` | 409 | 이미 사용 중인 닉네임입니다. | user |
| `MEMBER_004` | 404 | 가입된 정보가 없어요. | user |
| `MEMBER_005` | 401 | 이메일 또는 비밀번호가 올바르지 않아요. | user |
| `MEMBER_006` | 400 | 비밀번호가 올바르지 않아요. | user |
| `VERIFY_001` | 400 | 인증 코드가 일치하지 않아요. | user |
| `VERIFY_002` | 400 | 인증 코드가 만료됐어요. 다시 받아 주세요. | user |
| `VERIFY_003` | 400 | 이메일 인증을 먼저 완료해 주세요. | user |
| `VERIFY_004` | 429 | 잠시 후에 다시 요청해 주세요. | user |
| `VERIFY_005` | 429 | 인증 시도 횟수를 초과했어요. 1시간 후에 다시 시도해 주세요. | user |
| `MAIL_001` | 500 | 메일 발송에 실패했어요. 잠시 후 다시 시도해 주세요. | user |
| `PROFILE_001` | 404 | 등록된 조건이 없습니다. | user, policy, recommendation |
| `REGION_001` | 404 | 존재하지 않는 지역입니다. | user, policy |
| `TERM_001` | 404 | 존재하지 않는 용어입니다. | policy |
| `POLICY_001` | 404 | 존재하지 않는 정책입니다. | policy(F5 포함) |
| `POLICY_002` | 502 | 온통청년 API 요청이 실패해 정책 수집이 중간에 멈췄어요. 잠시 후 다시 시도해 주세요. | policy |
| `CARD_NEWS_001` | 404 | 존재하지 않는 카드뉴스입니다. | policy |
| `POLICY_SEARCH_002` | 400 | 혜자는 주거 관련 혜택을 알려드려요. | recommendation |
| `POLICY_SEARCH_006` | 400 | 검색어를 입력해 주세요. | recommendation |
| `POLICY_SEARCH_007` | 400 | 검색어는 200자 이하여야 합니다. | recommendation |
| `FAVORITE_001` | 409 | 이미 등록된 관심 정책입니다. | engagement |
| `FAVORITE_002` | 404 | 등록되지 않은 관심 정책입니다. | engagement |
| `NOTIFICATION_001` | 404 | 존재하지 않는 알림입니다. | engagement |

`POLICY_001`은 Excel에 policy로만 적혀 있지만 engagement `ErrorStatus`에도 같은 코드·메시지로 정의되어 있고 [ENG-02](#eng-02)·[ENG-03](#eng-03)이 돌려줍니다.

`SERVER_002`는 코드와 HTTP 상태가 같지만 서비스마다 메시지와 조건이 다릅니다. engagement의 값은 [ENG-02](#eng-02)에서 외부로 나갑니다. policy의 값은 F6 실패를 나타내지만, 이를 부르는 POL-02·03·05가 모두 비로그인 응답으로 대체하므로 외부 응답에는 나오지 않습니다.

## 11. 참고 문서

- [Engagement API 상세](engagement-api.md): 스케줄러, 테스트 범위 등 engagement 구현 메모. API 계약이 이 문서와 다르면 이 문서가 기준입니다.
- [Gateway 라우팅·인증](../../infra/gateway-service/README.md), [Gateway → Engagement 내부 JWT](../architecture/gateway-engagement-auth.md)
- 서비스별 README: [user](../../services/user-service/README.md), [policy](../../services/policy-service/README.md), [engagement](../../services/engagement-service/README.md), [recommendation](../../services/recommendation-service/README.md)
- 실제 연동 검증: [05 테스트케이스](../05_테스트케이스/README.md)
