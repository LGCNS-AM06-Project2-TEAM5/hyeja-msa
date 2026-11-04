# engagement-service

담당: 이지원, 손수연 · 포트 8083 · DB `engagement_db` (favorite, notification, 정책 스냅샷 읽기 모델)

관심 정책(♡) 등록·해제·목록(키워드 검색·페이징), 관심 여부 일괄 조회, 마감 D-7 앱 알림 생성·조회·읽음·삭제, 정책 변경·회원 탈퇴 이벤트 구독, 탈퇴 이벤트 DLT 관리자 메일을 맡습니다. 마감 알림 메일 발송은 구현하지 않았습니다.

인증은 Gateway의 사용자 JWT 검증 후 전달되는 내부 JWT를 검증하는 방식입니다. Gateway와 동일한 별도 대칭키 `GATEWAY_ENGAGEMENT_SECRET`(최소 32 UTF-8 바이트)을 환경변수로 주입해야 합니다. `X-Member-Id`만 보낸 요청은 인증되지 않습니다. [내부 인증 설계](../../docs/architecture/gateway-engagement-auth.md)

## 외부 API (Gateway 경로)

| 메서드 | 경로 | 인증 |
|---|---|---|
| GET | `/api/favorite` | 로그인 |
| POST | `/api/favorite/{policyId}` | 로그인 |
| DELETE | `/api/favorite/{policyId}` | 로그인 |
| GET | `/api/notification` | 로그인 |
| PATCH | `/api/notification/{notificationId}/read` | 로그인 |
| DELETE | `/api/notification/{notificationId}` | 로그인 |

알림은 매일 00:05 Asia/Seoul에 스케줄러가 자동 생성하며 수동 생성 API는 제공하지 않습니다. 현재 동작과 테스트 방법은 [API 명세서](../../docs/api/engagement-api.md)를 참고하세요.

## 옮겨 올 코드 (hyeja-backend)

- [`domain/favorite`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/favorite), [`domain/notification`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/notification) (스케줄러 매일 00:05 Asia/Seoul)

## 다른 서비스와의 연결

| 방향 | 방식 | 내용 |
|---|---|---|
| 제공 → policy, recommendation | Feign | 회원의 관심 정책 ID 일괄 조회 |
| 사용 → policy | Feign | 관심 등록 시 정책 존재·스냅샷 확인 |
| 구독 | Kafka `policy-updated` | 정책 스냅샷 읽기 모델 갱신. `PolicyUpdatedListener`가 받아 `PolicySnapshotUpdater`로 반영하고, 같은 `eventId`는 한 번만 반영, 실패 시 3번 재시도 후 `policy-updated-dlt`. Kafka 주소가 있는 환경(Compose)에서만 켜짐 |
| 구독 | Kafka `member-withdrawn` | 탈퇴 회원의 관심 정책·알림 정리. `eventId`를 `processed_member_event`에 남겨 중복 무시(#110), 실패 시 1초 간격 3번 재시도 후 `member-withdrawn-dlt` (#1 확정) |
| 구독 | Kafka `member-withdrawn-dlt` | DLT 메시지를 `failed_member_event`에 저장하고 누적 건수가 임계값에 닿으면 관리자 메일 발송(아래) |
| (미구현) | Kafka `notification-created` | 마감 알림 메일 Worker는 구현하지 않았습니다. 토픽만 Compose가 만듭니다 |

## DLT 관리자 메일 (#107)

1. `MemberWithdrawnDltListener`가 `member-withdrawn-dlt` 메시지를 `failed_member_event`에 저장합니다. 같은 topic·partition·offset은 한 번만 저장·집계합니다.
2. `DltNotificationService`가 토픽별 누적 건수를 `dlt_alert_state`에 기록합니다.
3. 누적이 `ADMIN_DLT_THRESHOLD`(기본 3)에 닿으면 `ADMIN_ALERT_EMAIL`로 메일을 보내고, 성공하면 누적을 0으로 되돌립니다.
4. 발송에 실패하면 `ADMIN_DLT_RETRY_INTERVAL_MS`(기본 60000)마다 다시 보냅니다. `ADMIN_ALERT_EMAIL`이 비어 있으면 보내지 않습니다.

SMTP는 `MAIL_HOST`(기본 smtp.gmail.com)·`MAIL_PORT`(기본 587)·`MAIL_USERNAME`·`MAIL_PASSWORD`로 설정합니다. Compose health check는 mail health indicator를 꺼서 외부 SMTP 상태와 무관합니다. `policy-updated-dlt`는 메일 대상이 아니고 아래 지표로만 셉니다.

## Kafka 처리 지표 (#111)

`/actuator/prometheus`에 `hyeja_policy_updated_events_total{result="applied"|"duplicate"}`(스냅샷 반영·중복 무시)와 `hyeja_policy_updated_dlt_total`(DLT 전송 성공 수)을 노출합니다. 서비스 시작 이후 누적값입니다.

## 정책 스냅샷 읽기 모델

관심 목록 응답과 키워드 검색(정책명·지원 내용 LIKE)이 지금은 policy를 JOIN합니다. 같은 결과를 내려면 아래 값을 engagement에 복사해 둡니다.

`policyId, policyName, categories, supportContent, applyEndDate, applyPeriodCode, applyUrl, activeYn, deleted`

## 관심 등록 F5 연동 (#90)

- `FavoriteService.createFavorite`는 로컬 스냅샷이 있어도 매번 Policy의 `GET /internal/policies/{policyId}`를 호출합니다.
- 기본 호출 대상은 Eureka의 `policy-service`입니다. 직접 주소가 필요한 환경에서는 `CLIENTS_POLICY_URL`로 지정할 수 있습니다.
- 기본 연결 제한은 1초, 응답 제한은 2초입니다. `POLICY_CONNECT_TIMEOUT_MS`·`POLICY_READ_TIMEOUT_MS`로 변경할 수 있습니다. Feign 재시도는 기본 비활성 상태를 유지합니다.
- 외부 호출 후 `FavoriteRegistrationService.register`의 로컬 트랜잭션에서 공통 `PolicySnapshotUpdater.upsert`와 관심 저장을 처리합니다. F5의 `deleted=false`도 로컬 삭제 표시에 반영합니다. 저장 실패·중복 등록이면 스냅샷 변경도 롤백됩니다.
- F5의 `404 / POLICY_001`은 그대로 반환합니다. 그 외 HTTP 오류·연결 실패·시간 초과·비정상 응답은 `503 / SERVER_002`로 처리하며, 기존 스냅샷으로 대신 등록하지 않습니다.
- 신청 기간은 추가 검사하지 않습니다. 마감·CLOSED 정책도 F5가 정상 반환하면 등록할 수 있습니다. 관심 삭제는 기존 로컬 조회 방식을 유지하며 F5를 호출하지 않습니다.
- `/internal/**`는 외부 Gateway 경로로 노출하지 않으며 별도 `X-Internal-Token`은 붙이지 않습니다. 외부 관심 API의 Gateway 인증은 기존 방식을 유지합니다.
- 검증: `./gradlew test bootJar --no-daemon --no-watch-fs` 성공, 최신 develop 통합 후 전체 57개 테스트 통과(신규 F5 HTTP·저장 통합 26개, Reader 2개 포함).
- #88 공통 저장기와 #84 F3·F4 API가 머지된 develop에 통합한 작업입니다. 자동 검증은 모의 HTTP 서버를 실제 Feign으로 호출하고 H2에 저장합니다. 실제 Policy·Eureka·Gateway·MariaDB 연동은 별도 검증 대상입니다.

### F5 Circuit Breaker (Issue #103 후속)

- 적용 범위: `PolicySnapshotReader.getPolicy`의 `PolicyClient.getPolicy` 호출만 독립 회로 `engPolicySnapshot`(Resilience4j, `CircuitBreakerRegistry.executeSupplier`)으로 감쌉니다. Feign 전역 회로·`@CircuitBreaker`·Fallback·Retry는 쓰지 않으며, 연결 1초·응답 2초 제한은 그대로입니다.
- 설정(`application.yml`의 `resilience4j.circuitbreaker`, policy·recommendation과 같은 값): COUNT_BASED 20회, 최소 10회, 실패율 50%, OPEN 15초, HALF_OPEN 시험 3회, OPEN→HALF_OPEN 자동 전환 끔.
- 실패율 집계: 5xx·연결 실패·Timeout·역직렬화 실패는 실패로 셉니다. 4xx(`404 / POLICY_001`, 401, 403 등)는 `CircuitBreakerIgnorePredicate`로 제외합니다.
- 오류 응답은 바뀌지 않습니다. `404 / POLICY_001`은 그대로, 그 밖의 4xx·5xx·연결 실패·Timeout·비정상 응답과 회로 OPEN(`CallNotPermittedException`)은 `503 / SERVER_002`입니다. 실패하면 F5 호출 뒤에 시작하는 `FavoriteRegistrationService` 트랜잭션에 들어가지 않으므로 스냅샷 갱신·관심 저장이 없고, 기존 스냅샷으로 대신 등록하지도 않습니다.
- 관측: 상태 전환은 회로 이름만 담은 WARN 로그(`Circuit breaker state changed. name=engPolicySnapshot, ...`), 상태·호출 결과는 기존 Basic 인증 `/actuator/prometheus`의 `resilience4j_circuitbreaker_*` 메트릭으로 확인합니다. 회로가 OPEN이어도 `/actuator/health`는 UP입니다.
- 검증: `gradlew.bat test bootJar` 성공, 전체 76개 테스트 통과(신규 `PolicySnapshotCircuitBreakerTest` 11개·`PolicySnapshotCircuitBreakerIntegrationTest` 8개, 기존 `PolicyFavoriteIntegrationTest` 26개 포함). 실제 Policy 장애 상황의 Compose 실측은 별도 검증 대상입니다.

## 바뀌는 점

- favorite·notification의 member·policy FK를 지우고 ID만 저장합니다.
- 알림 대상 계산은 회원 탈퇴 여부와 정책 노출·마감 상태를 이 서비스 안의 데이터로 판단합니다.
