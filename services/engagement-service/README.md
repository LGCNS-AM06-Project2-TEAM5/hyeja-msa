# engagement-service

담당: 손수연 · 포트 8083 · DB `engagement_db` (favorite, notification, 정책 스냅샷 읽기 모델)

관심 정책(♡) 등록·해제·목록(키워드 검색·페이징), 관심 여부 일괄 조회, 마감 D-7 앱 알림 생성·조회·읽음·삭제, (여유 있을 때) 마감 알림 메일 발송을 맡습니다.

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
| 발행·구독 | Kafka `notification-created` | 내부 Worker가 마감 알림 메일 발송 (여유 있을 때, #1 확정) |
| 구독 | Kafka `member-withdrawn` | 탈퇴 회원의 관심 정책·알림 정리. `eventId`로 중복 무시, 실패 시 재시도 후 DLT (#1 확정) |

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
- 검증: `./gradlew test bootJar --no-daemon --no-watch-fs` 성공, 전체 43개 테스트 통과(신규 F5 HTTP·저장 통합 26개, Reader 2개 포함).
- #88의 공통 저장기 위에 쌓은 작업입니다. 자동 검증은 모의 HTTP 서버를 실제 Feign으로 호출하고 H2에 저장합니다. 실제 Policy·Eureka·Gateway·MariaDB 연동은 별도 검증 대상입니다.

## 바뀌는 점

- favorite·notification의 member·policy FK를 지우고 ID만 저장합니다.
- 알림 대상 계산은 회원 탈퇴 여부와 정책 노출·마감 상태를 이 서비스 안의 데이터로 판단합니다.
