# Engagement–Policy F5 실제 서비스 연동 검증 — Issue #112

- 검증일: 2026-11-04 (Asia/Seoul)
- 작업 브랜치: `test/112/f5-live-verification`
- 기준 커밋: `583587017afe06176386f24a8abb0a3e2e95dca8` — PR #92가 머지된 develop
- 관련 구현: F5 제공 #65, 소비·저장 #92, F3·F4 제공 #84
- 결과: 실제 서비스 간 확인 항목 **35개 모두 통과**. 운영 코드 수정 없음.

## 1. 실행 범위

```text
검증용 Gateway assertion을 포함한 HTTP 요청
  → 실제 Engagement Service /api/favorite/{policyId}
  → OpenFeign + LoadBalancer
  → 실제 Eureka의 policy-service 인스턴스 탐색
  → 실제 Policy Service /internal/policies/{policyId}
  → Policy 전용 MariaDB
  → Engagement 전용 MariaDB의 policy_snapshot·favorite 저장
```

두 서비스는 서로 다른 Java 프로세스이며, 모의 Policy HTTP 서버를 사용하지 않았다.
`clients.policy.url`을 지정하지 않아 서비스명 기반 통신을 검증했다.
Policy 요청 로그의 `/internal/policies/{policyId}` 호출 증가와 실제 DB 변경을 함께 확인했다.

| 항목 | 환경 |
|---|---|
| Java | 17, 서비스별 JVM `-Xms64m -Xmx384m` |
| DB | 로컬 MariaDB 13.0.2, TCP 3307 |
| Schema | `f5_112_policy_9916dd`, `f5_112_engagement_9916dd` |
| DB 계정 | 각 서비스 전용 계정, 자기 검증 DB에만 권한 부여 |
| 서버 | Eureka 53943, Policy 53944, Engagement 53945, 모두 127.0.0.1 |
| 프로필 | `verify112` |
| F5 Timeout | 연결 1초·응답 2초, 서비스 기본 설정 유지 |
| Config Server·Cloud Bus·Kafka | 이번 F5 검증에서 사용하지 않음 |
| 외부 API·OpenAI | 호출하지 않음. 기동용 가짜 값만 주입 |

Docker 데몬이 실행되지 않아 로컬 JVM으로 검증했다. 기존 DB와 #73 검증 DB는 수정하지 않았다.
서비스가 사용하는 DB는 분리했고, 검증 실행기만 전용 DB에 합성 데이터를 준비하고 결과를 조회했다.
로컬 작업 폴더의 Git 미추적 복사본은 원본과 같은 파일임을 확인했고, 커밋된 소스를 별도로 추출해 빌드했다.

## 2. 자동 테스트·빌드

각 모듈에서 `./gradlew test bootJar --no-daemon --no-watch-fs --max-workers=2` 실행.

| 모듈 | 테스트 | 실패·오류·건너뜀 | 빌드 |
|---|---:|---:|---|
| Policy | 247 | 0 | 성공 |
| Engagement | 57 | 0 | 성공 |
| Eureka | 0 (테스트 없음) | 0 | 성공 |

## 3. 실제 연동 결과

| 상황 | 기대 결과 | 실제 결과 |
|---|---|---|
| 빈 Engagement DB에서 관심 등록 | F5 조회 후 스냅샷·관심 생성 | 200/SUCCESS_001, 스냅샷1·관심1 |
| DTO 매핑 | ID·이름·카테고리·null 유지 | 응답과 원본 대조 통과 |
| 스냅샷 존재 상태에서 새 회원 등록 | F5 재조회·최신 값 갱신 | 정책명·지원 내용·카테고리 변경 반영, 스냅샷1·관심2 |
| 같은 회원의 중복 등록 | 409/FAVORITE_001 | 통과 |
| 중복 요청 직전 원본 정책명 변경 | 실패 시 로컬 스냅샷도 롤백 | 기존 로컬 정책명 유지, 관심 수 불변 |
| 없는 정책 | 404/POLICY_001, 저장 없음 | 통과 |
| 비활성 정책 | 404/POLICY_001, 저장 없음 | 통과 |
| 삭제 정책 | 404/POLICY_001, 저장 없음 | 통과 |
| 마감일 경과·CLOSED 정책 | 기존 계약대로 등록 허용 | 200, CLOSED·2026-01-01 유지 |
| 관심 목록 | 회원 자신의 관심 반환 | 회원별 목록·건수 확인 |
| F3·F4 회귀 확인 | 등록된 관심 ID만 반환 | 실제 /internal/favorites/active-policy-ids 응답 일치 |
| Policy 프로세스 일시 정지 | 읽기 제한 후 503, 저장 없음 | 2.014초, 503/SERVER_002, 기존 스냅샷·관심 불변 |
| Policy 프로세스 종료 | 503, 오래된 스냅샷으로 등록하지 않음 | 0.009초, 503/SERVER_002, 저장 없음 |
| Policy 종료 중 관심 해제 | 로컬 삭제 성공, 다른 회원 유지 | 200, 회원별 목록 재확인 |
| Policy 재시작 후 새 회원 등록 | 정상 복구·최신 값 반영 | 200, 최신 원본명 반영 |

위 시나리오에서 HTTP 상태·오류 코드·응답 필드·DB 건수 등 총 35개 단언을 확인했다.
처리 시간은 이 로컬 환경의 단일 요청 관측값이며 성능·부하 테스트 결과가 아니다.
검증 데이터는 `F5-112-A`, `F5-112-INACTIVE`, `F5-112-DELETED`, `F5-112-CLOSED` 합성 정책이다.
`F5-112-MISSING`은 DB에 없는 ID로 사용했다. 실제 정책 원문 수집은 하지 않았다.

## 4. 재현 절차

1. 기준 커밋에서 Policy·Engagement·Eureka의 테스트와 bootJar를 실행한다.
2. MariaDB에 검증용 DB 2개와 각 DB 전용 계정을 생성한다. 기존 서비스 DB 대신 검증 DB를 사용한다.
3. Eureka를 사용하지 않는 로컬 포트에 실행하고 Policy·Engagement를 각각 별도 JVM으로 실행한다.
4. 아래 실행 설정을 맞추고 `/actuator/health`와 Eureka 등록 상태를 확인한다.
5. Policy 검증 DB에 합성 정책을 준비한 뒤 위 표 순서대로 관심 등록·조회·삭제를 호출한다.
6. 각 성공/실패 요청 후 Engagement 검증 DB의 스냅샷과 관심 행을 조회한다.
7. 자신이 띄운 Policy 프로세스만 일시 정지·재개하여 읽기 제한을 확인하고, 종료·재시작하여 실패·복구를 확인한다.
8. 검증용 프로세스를 종료한다. 테스트 데이터·로그는 사용자 확인용으로 별도 보관한다.

필수 실행 설정:

- `SPRING_PROFILES_ACTIVE=verify112`
- `SPRING_CONFIG_IMPORT` 빈 값, Config Client·Cloud Bus 비활성
- 서비스별 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update`, `SPRING_JPA_OPEN_IN_VIEW=false`
- 서비스별 `SERVER_PORT`, `SERVER_ADDRESS=127.0.0.1`
- Engagement `GATEWAY_ENGAGEMENT_SECRET`: 검증 전용 무작위 값
- Policy `OPEN_AI_KEY`, `OPEN_AI_MODEL`, `YOUTH_API_KEY`: 호출하지 않는 기동용 가짜 값
- Eureka 주소는 두 클라이언트 JVM의 실행 인자 `--eureka.client.service-url.defaultZone=http://127.0.0.1:<EUREKA_PORT>/eureka/`로 명시
- 검증에서는 Eureka 클라이언트 등록 초기 지연·목록 갱신 및 서버 응답 캐시 주기를 1초로 줄이고 LoadBalancer 캐시를 비활성화
- `clients.policy.url`은 비워 두어 실제 서비스명 해석을 사용

첫 기동에서는 환경변수의 Eureka 주소가 적용되지 않아 기본 8761로 연결을 시도했다.
별도 포트의 검증 서버를 다시 실행하면서 위 JVM 인자로 주소를 명시했고 최종 검증은 정상 등록 후 수행했다.
이 변경은 실행 설정이며 운영 코드 수정이 아니다.

외부 관심 API는 기존 Gateway assertion 검증을 유지한다. 이번 실행기는 검증용 키로 다음 claim을 가진 HS256 assertion을 생성했다.

- issuer: gateway-service
- audience: engagement-service
- subject: 합성 회원 ID 11201/11202/11203
- issuedAt: 요청 시각, expiration: 120초 이내

실제 키·비밀번호·assertion 원문은 이 문서와 Git에 기록하지 않는다.
실제 Gateway 로그인으로 얻은 사용자 토큰을 사용한 검증으로 표현하지 않는다.

## 5. 완료 범위와 후속 작업

- #112의 F5 정상·오류·저장·기존 기능 확인 조건을 로컬 실제 서비스 조합에서 검증했다.
- 실제 Gateway·User 로그인·프런트 화면·Docker Compose/CD 배포는 이번 범위 밖이다.
- policy-updated·member-withdrawn·Kafka/Outbox/중복/DLT 종단간 검증은 #9 등 별도 범위다.
- Recommendation·F2·검색·서킷 브레이커는 검증하지 않았다. F2 실제 검색 흐름 검증은 #77에서 진행한다.
- 초기 서버 설정 실패 및 프로세스 종료 권한 문제를 최종 검증 결과와 구분했다. 최종 실행기는 자신이 시작한 서버를 종료하는 방식으로 정상 마무리했다.
