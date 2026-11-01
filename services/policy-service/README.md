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

## 분담 (11-01 합의)

| 담당 | 범위 |
|---|---|
| 김예준 | 정책 수집(`/sync`)·OpenAI 분석, 정책 목록(`/housing`)·상세(비로그인), `PolicyController`·`PolicyRepository`, 내부 API 제공(F2·F5) |
| 홍준서 | 공통 기반(global, 엔티티 5개, 지역·용어 초기 데이터), 지역·용어·카드뉴스, 로그인 기능(`/housing/me`, 상세 판정·하트, 회원 카드뉴스), user·engagement 호출(F6·F4), `policy-updated` 발행(#9) |

- 1차 `PolicyService`는 `PolicySyncService`·`PolicyQueryService`(김예준)와 `PolicyMemberService`(홍준서)로 나눕니다.
- 옮긴 코드의 1차 테스트도 함께 옮기고, 1차 응답과 같은지 확인한 뒤 PR을 올립니다.

## 패키지 구조

`com.hyeja.policyservice` 아래에 1차 `com.hyeja.domain.*`, `com.hyeja.global.*`를 한 단계 줄여 둡니다.

| 패키지 | 내용 |
|---|---|
| `global` | 공통 응답(`ApiResponse`·`ErrorStatus`), 예외 처리, `BaseEntity`, JPA Auditing, `Clock`(Asia/Seoul), 로그인 헤더 읽기 |
| `policy`, `cardnews`, `region`, `term` | 1차와 같은 하위 구조(`entity`, `enums`, `converter`, `repository`, `service`, `controller`, `dto`) |
| `client` | 다른 서비스 호출(Feign) |
| `internal` | 다른 서비스에 주는 내부 API |
| `event` | Kafka 이벤트 발행 |

## 로그인 회원 정보

policy-service에는 Spring Security·JWT 코드가 없습니다. Gateway가 JWT를 검증한 뒤 `X-Member-Id`·`X-Member-Role` 헤더로 넘겨주고, 외부에서 보낸 같은 이름의 헤더는 Gateway가 지웁니다. 관리자 권한(`/sync`)도 Gateway가 검사합니다.

컨트롤러에서는 1차의 `@AuthenticationPrincipal Long memberId` 대신 `@LoginMemberId Long memberId`를 씁니다. 헤더가 없으면 401(`COMMON_002`)이고, 로그인이 선택인 API는 `@LoginMemberId(required = false)`로 받으면 비로그인일 때 `null`입니다.

## 연결 지점 (먼저 만들어 둔 빈 구현)

| 지점 | 쓰는 쪽 | 지금 동작 | 채우는 시점 |
|---|---|---|---|
| `PolicyMemberService.getMemberDetail(policy, memberId)` → `PolicyMemberDetailDTO` | 정책 상세 | 하트 false, 판정 없음(비로그인과 같음) | 3단계, F4·F6 연결 |
| `PolicyChangedPublisher.publish(policyIds)` | 정책 수집 저장 후 | 아무것도 보내지 않음(`NoOpPolicyChangedPublisher`) | 4단계, `policy-updated` 발행 |

상세 응답의 `isFavorite`·`overallStatus`·`conditions`는 `PolicyMemberDetailDTO` 값을 그대로 옮겨 담습니다. 판정 항목 형식은 `PolicyMemberDetailDTO.ConditionResultDTO`입니다.

## 초기 데이터 (dev)

- 지역 256건: `csv/region_sigungu.csv`를 `RegionDataInitializer`가 앱 시작 때 넣습니다(있으면 이름만 갱신). test 프로필에서는 실행하지 않습니다.
- 용어 50건: `db/seed/term-data.sql`. 같은 용어는 건너뜁니다. `DB_SEED_MODE=never`로 끌 수 있습니다.
- 스키마 기준은 [`docs/04_ERD_테이블명세서/policy_db.sql`](../../docs/04_ERD_테이블명세서/policy_db.sql)입니다. 이 SQL로 만든 표에 `ddl-auto=validate`로 기동해 엔티티와 일치함을 확인했습니다.

## 로컬 개발 실행

- `application.yml`: 서비스 이름, Config Server 연결, 랜덤 포트 등 공통 설정.
- `application-dev.yml`: 로컬 MariaDB 및 JPA 개발 설정. 실제 비밀값 없이 공유하므로 두 파일 모두 커밋합니다.

IntelliJ의 PolicyServiceApplication 실행 설정에 환경변수를 지정합니다.

```text
POLICY_DB_PASSWORD=<로컬 policy_user 비밀번호>
```

DB 기본값은 `jdbc:mariadb://localhost:3307/policy_db`, 사용자명은 `policy_user`입니다.
환경이 다르면 `POLICY_DB_URL`, `POLICY_DB_USERNAME`도 지정합니다.
비밀번호는 파일에 저장하지 않습니다. `.env`는 Spring Boot가 자동으로 읽지 않으므로 실행 환경에서 주입합니다.

`dev`에서만 `ddl-auto: update`와 SQL 출력이 적용됩니다. 실행 시 DB 스키마가 갱신될 수 있습니다.
다른 환경에서는 Config Server 또는 환경변수로 해당 환경의 datasource/JPA 설정을 제공해야 합니다.
프로필을 지정하지 않으면 `spring.profiles.default: dev`에 따라 `dev`가 기본 적용됩니다.
다른 환경은 `SPRING_PROFILES_ACTIVE`로 명시합니다.

현재 실제 포트는 `server.port: 0`으로 매번 배정됩니다. 로그의 포트를 사용해
`http://localhost:<포트>/health-check`에서 `policy-service OK`를 확인합니다.

## 테스트와 빌드

```bash
./gradlew test bootJar
```

기본 컨텍스트 테스트는 `@ActiveProfiles("test")`로 테스트 전용 설정을 사용합니다.
H2 메모리 DB를 사용하고 Config Server·Eureka 연동을 비활성화하므로 로컬 MariaDB 비밀번호가 필요하지 않습니다.
이 테스트는 컨텍스트 초기화 확인용이며 실제 MariaDB 연결·스키마 호환성은 dev 환경에서 별도로 검증합니다.
