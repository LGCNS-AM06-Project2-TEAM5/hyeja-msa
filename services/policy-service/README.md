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

## 결정된 사항 (#1)

- 정책 상세(로그인)의 자격 판정은 recommendation을 호출하지 않고, 판정 코드(PolicyEligibilityEvaluator 등)를 policy와 recommendation이 같이 씁니다. 판정 규칙의 기준은 recommendation이 갖고, 우선 두 서비스에 같은 코드와 같은 테스트를 둡니다.
- 지역 데이터는 user_db에도 복사본을 두므로 user가 policy의 지역을 호출하지 않습니다.

## 연결 지점 (먼저 만들어 둔 빈 구현)

수집·조회 쪽과 회원 기능·이벤트 쪽이 서로 기다리지 않도록 아래 두 곳을 먼저 만들어 뒀습니다(11-01 분담 합의).

| 지점 | 쓰는 쪽 | 지금 동작 | 채우는 시점 |
|---|---|---|---|
| `PolicyMemberService.getMemberDetail(policy, memberId)` → `PolicyMemberDetailDTO` | 정책 상세 | 하트 false, 판정 없음(비로그인과 같음) | user(F6)·engagement(F4) 연결 |
| `PolicyChangedPublisher.publish(policyIds)` | 정책 수집 저장 후 | 아무것도 보내지 않음(`NoOpPolicyChangedPublisher`) | `policy-updated` 발행(#9) |

상세 응답의 `isFavorite`·`overallStatus`·`conditions`는 `PolicyMemberDetailDTO` 값을 그대로 옮겨 담으면 됩니다.

## 다른 서비스 호출 (Feign)

| 호출 | 클라이언트 | 실패할 때 |
|---|---|---|
| F6 회원 조건 (→ user `GET /internal/members/{memberId}/profile`) | `UserClient`는 직접 쓰지 않고 `MemberProfileReader.getActiveProfile`을 씁니다 | `MEMBER_001`·`PROFILE_001`은 그대로 돌려주고, 연결 실패·시간 초과·5xx는 503 `SERVER_002`. 정책 상세는 이 경우 비로그인 응답으로 대신합니다 |

시간 제한은 `application.yml`의 `spring.cloud.openfeign.client.config.default`(연결 1초, 응답 2초)입니다. 서비스 주소는 Eureka에서 찾습니다.

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

## OpenAI 정책 분석

원본 `Hyejabonjuui/backend`의 `3e47bb1` 기준 정책 분석 코드를 이관했습니다.
`PolicyAiAnalyzer`는 정책 수집용 분석 인터페이스이며 검색·추천 분석은 포함하지 않습니다.
프롬프트·JSON Schema·카테고리 및 소득 보정·실패 전달 규칙은 기존 코드와 동일합니다.

실행 환경에 다음 변수를 지정합니다. 실제 키를 yml이나 Git에 저장하지 않습니다.

```text
OPEN_AI_KEY=<본인의 OpenAI API 키>
OPEN_AI_MODEL=<기존 프로젝트에서 사용하던 모델명>
```

키와 모델은 애플리케이션 기동에 필요합니다. IntelliJ 실행 설정의 환경변수는 터미널에 자동 전달되지 않습니다.
기본 `test`는 테스트 전용 가짜 키와 모델 및 모의 클라이언트를 사용하며 외부 API를 호출하지 않습니다.
SDK에 필요한 Jackson 2 ObjectMapper와 Spring MVC의 Jackson 3 매퍼를 구분해 유지합니다.

실제 호출 검증은 두 환경변수가 설정된 터미널에서 별도로 실행합니다.

```bash
./gradlew openAiIntegrationTest
```

이 작업은 샘플 정책 1건을 실제 OpenAI로 분석하므로 비용과 외부 네트워크가 필요합니다.
키·모델이 없으면 명확한 환경변수 안내와 함께 실패합니다. 매 실행 시 실제 검증하도록 이전 결과를 재사용하지 않습니다.
성공 시 응답 파싱과 필수 분석 값의 존재를 확인하며, 가변적인 문구 자체를 고정 값과 비교하지 않습니다.
실제 정책 수집·DB 저장·`/sync` 연결은 후속 작업입니다.

## 정책 수집·동기화 (`POST /api/policies/sync`)

원본 `3e47bb1`의 수집 로직을 이관했습니다. 실행 환경에 `YOUTH_API_KEY`를 추가합니다.
기본 온통청년 주소는 `https://www.youthcenter.go.kr/go/ythip/getPlcy`입니다.
OpenAI 분석에는 앞 절의 `OPEN_AI_KEY`·`OPEN_AI_MODEL`도 필요합니다.

- 페이지당 100건, 최대 50페이지를 조회하고 주거·승인 정책만 선별합니다.
- 페이지 요청이 실패하면 1회 재시도하며, 다시 실패하면 502 `POLICY_002`와 `stoppedPage`, `savedCount`를 반환합니다.
- 정책별 AI 분석 후 `PolicySyncItemService.save`의 독립 트랜잭션으로 정책·지역·누락 카드뉴스를 저장합니다. 개별 분석·저장 실패는 건너뛰고 다음 정책을 처리합니다.
- 재수집은 정책과 지역 연결을 갱신하며 기존 카드뉴스는 유지하고 없는 번호만 생성합니다. 지역 코드가 없거나 `00000`이면 전국, 시·도 코드는 기존 시군구로 확장합니다.
- 성공 응답은 기존 `SUCCESS_001`과 `온통청년 주거 정책 N건 동기화가 완료되었습니다.` 문구를 유지합니다. N은 성공 처리한 항목 수입니다.

Gateway의 `SecurityConfig`는 이 경로를 ADMIN 전용으로 제한하고 `JwtAuthenticationWebFilter`가
외부 회원 헤더를 제거한 뒤 검증된 `X-Member-Id`, `X-Member-Role`을 설정합니다.
Policy Controller에서도 양의 회원 ID와 ADMIN 역할을 확인합니다. Policy에는 Security·JWT를 추가하지 않습니다.
헤더는 Gateway를 신뢰하는 내부 계약이므로 Policy 포트를 외부에 직접 노출하지 않는 배포 구성이 전제입니다.
PR #37의 공통 회원 헤더 리졸버는 현재 브랜치에 없어 직접 헤더를 읽으며, 반영 후 공통화할 수 있습니다.

카드뉴스 Entity·Repository는 현재 팀원 구현을 사용합니다. `policy-updated` 발행은 아직 연결하지 않았으며
이벤트 담당자가 `PolicySyncItemService.save`의 저장 트랜잭션과 발행 경계를 맞춰야 합니다.
현재 코드가 Kafka 동기화까지 보장하는 것은 아닙니다.

자동 테스트는 외부 API·AI를 대체하며, H2에서 재수집·카드 중복 방지·항목 롤백·앞선 커밋 유지까지 확인합니다.
실제 온통청년·OpenAI 대량 호출, MariaDB 쓰기, Gateway 경유 통합 실행은 별도 검증입니다.

## 비회원 정책 목록·상세 조회

- `GET /api/policies/housing`: category(생략 시 전체), sort(DEADLINE/VIEW_COUNT/NAME, 기본 DEADLINE), page(0 이상, 기본 0), size(1~50, 기본 8).
- 목록은 삭제·비활성·CLOSED·신청 시작 전·마감일 경과 정책을 제외합니다. 오늘 시작/마감 정책은 포함합니다. 원본 쿼리와 정렬을 유지하며 같은 정렬 값은 정책 ID로 순서를 고정합니다.
- 지역은 페이지의 정책 ID로 한 번에 조회합니다. 응답의 snake_case 필드, 지역 목록·전국 여부·지역 요약, 한국 날짜 기준 d_day와 페이지 메타데이터는 원본과 동일합니다.
- `GET /api/policies/{policyId}`: 원본 정책 상세 필드와 추가 자격 문구에 포함된 용어를 반환합니다. 없는 정책은 404 POLICY_001입니다. 상세는 원본의 ID 조회 동작을 유지하며 목록 필터를 추가 적용하지 않습니다.
- 비회원 상세는 isFavorite=false, overallStatus=null, conditions=[]입니다. 회원 ID가 있으면 `PolicyMemberService.getMemberDetail(policy, memberId)` 결과를 응답에 옮깁니다.
- `PolicyMemberService`, `PolicyMemberDetailDTO`는 미머지 PR #40의 ad09a109 기준 코드를 동일하게 가져온 연결 지점입니다. 현재 빈 구현이므로 로그인 상세도 기본값을 반환하며, 실제 프로필·관심 여부·판정 구현 완료를 뜻하지 않습니다. #40 머지 시 두 파일의 동일성과 최종 연결을 확인합니다.
- 현재 브랜치에는 #37의 공통 회원 헤더 리졸버가 없어 Gateway의 X-Member-Id를 직접 읽습니다. 잘못된 ID 또는 회원 ID 없이 Authorization만 전달된 상세 요청은 401로 거부하며 JWT를 직접 해석하지 않습니다.
- 원본 목록 DTO의 Swagger 설명용 어노테이션은 현재 서비스에 Swagger 의존성이 없어 제외했으며 JSON 매핑은 유지했습니다. 지역 요약은 원본 summarize 규칙만 목록 Converter에 옮겼습니다.

Issue #45는 선행 수집 PR #44 위의 작업입니다. 회원 연결 지점은 #40과의 의존성이 있으며 회원 업무·Feign·F2/F5 구현은 이번 범위에 포함하지 않습니다.


## F2 내부 정책 후보 조회 (Issue #53)

`POST /internal/policies/candidates`로 카테고리·시군구 코드·기준 날짜를 받아
추천·검색에 필요한 정책 후보 최대 20건과 전체 대상 지역·자격 조건을 반환합니다.
원본 `3e47bb1`의 후보 조회 규칙을 유지하며 판정·관심 조회·AI 호출은 수행하지 않습니다.
날짜는 Recommendation이 전달하며 Policy 서버의 오늘 날짜로 덮어쓰지 않습니다.
요청·응답·오류·내부 인증 미확정 상태는 [F2 계약](../../docs/api/README.md#f2-정책-후보-조회-issue-53)을 따릅니다.
기존 `/api/policies/housing` 목록과 F5 구현은 이번 작업 범위에 포함하지 않습니다.
