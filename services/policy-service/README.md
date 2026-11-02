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
