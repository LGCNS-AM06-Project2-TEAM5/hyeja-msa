# gateway-service

담당: 이지원 · 포트 8000

Spring Cloud Gateway. 프런트는 이 서비스만 호출합니다. 기존 29개 API의 경로·메서드를 그대로 유지합니다.

## 라우팅 (위에서부터 먼저 매칭)

| 순서 | 경로 | 보낼 서비스 |
|---|---|---|
| 1 | `GET /api/policies/search` | recommendation-service |
| 2 | `/api/policies/**`, `/api/regions`, `/api/terms/**` | policy-service |
| 3 | `/api/members/**` | user-service |
| 4 | `/api/favorite/**`, `/api/notification/**` | engagement-service |

`/api/policies/search`가 `/api/policies/**`보다 먼저 매칭되도록 순서를 지정합니다.

## 인증 (기존 혜자 JWT 규칙 유지)

- JWT 서명 확인과 Redis 로그아웃 블랙리스트 확인 후, 회원 ID·권한을 헤더로 각 서비스에 전달합니다.
- 로그인 없이 허용: `POST /api/members`, `/api/members/login`, `/api/members/email-verifications/**`, `GET /api/members/find-email`, `/api/regions`, `/api/terms/**`, `/api/policies/housing`, `/api/policies/card-news/guest`, `/api/policies/card-detail/*`, `/api/policies/{policyId}`, `/api/health`
- 로그인 필요(위 허용 경로보다 우선): `GET /api/policies/search`, `GET /api/policies/card-news`
- 관리자 전용: `POST /api/policies/sync`, `/api/notification/admin/**`
- 로그인 없이 허용된 경로라도 **잘못된 토큰을 보내면** 지금처럼 오류를 돌려줘야 합니다(정책 상세 등). 각 서비스에서도 필요한 권한을 다시 확인합니다.
- CORS: `http://localhost:5173` 허용

참고 코드: `ljw2869/msa`의 `apigateway-service`

## 로컬 실행

Java 17을 사용합니다. Config Server(8888), Discovery(8761)를 먼저 실행한 뒤
저장소 루트에서 다음 명령을 실행합니다.

```powershell
cd infra/gateway-service
.\gradlew.bat bootRun
```

- Eureka 대시보드: http://localhost:8761 (`GATEWAY-SERVICE`가 `UP`인지 확인)
- Gateway 헬스 체크: http://localhost:8000/actuator/health
- Config 설정 조회: http://localhost:8888/gateway-service/local
- 빌드: 서비스 디렉터리에서 `.\gradlew.bat build`
- 종료: 실행한 터미널에서 `Ctrl+C`

기본 프로필은 `local`입니다. Config 연결은 필수이며 `CONFIG_SERVER_URL`
환경변수로 주소를 변경할 수 있습니다. Eureka 주소는 원격 공통 설정에서 읽습니다.
현재 포트와 라우팅은 Gateway의 로컬 `application.yml`에 있습니다.
원격 관리로 전환할 때 해당 항목을 `hyeja-config/gateway-service.yml`로 옮기고
Gateway를 재시작하면 됩니다. 원격 파일은 이 작업에서 변경하지 않습니다.

원래 API 경로를 변경하지 않고 전달합니다. 대상 서비스가 Eureka에 등록되어
있지 않으면 해당 경로 요청은 503으로 응답할 수 있습니다.

## 인증 실행 환경

- `JWT_SECRET`: User 서비스와 동일한 UTF-8 서명 키(최소 32바이트). 필수이며 기본값은 없습니다.
- `REDIS_HOST`: User 서비스가 사용하는 Redis 호스트(기본 `localhost`)
- `REDIS_PORT`: Redis 포트(기본 `6379`)
- `REDIS_DATABASE`: Redis DB 번호(기본 `0`, User 서비스와 같아야 함)

IntelliJ Run Configuration의 Environment variables 또는 실행 터미널의 환경변수로
주입합니다. `.env` 파일은 자동으로 읽지 않으며 실제 키는 Git에 커밋하지 않습니다.
JWT 키가 없거나 너무 짧으면 Gateway가 기동하지 않습니다.

`JwtVerifier`는 기존 User 토큰의 `sub`(회원 ID), `role`, `exp`와 서명을 검증합니다.
Gateway는 로그인/회원 DB 조회/토큰 발급을 수행하지 않습니다.
원래 Authorization 헤더를 그대로 전달하여 User의 기존 인증 필터와
`@AuthenticationPrincipal`, 로그아웃 처리를 유지할 수 있습니다.

블랙리스트는 `blacklist:<토큰 원문>` 키 존재 여부를 Reactive Redis로 조회합니다.
User가 로그아웃/탈퇴 시 값 `logout`, TTL=토큰 만료까지 남은 시간으로 저장하며,
Gateway는 저장/삭제하지 않습니다. Redis 조회 실패 시 503 `GATEWAY_001`로 차단합니다.
실제 User 로그아웃과 공유 Redis 연동은 User 서비스 연결 후 추가 확인해야 합니다.

외부의 `X-Member-Id`, `X-Member-Role`은 제거한 뒤 검증된 값으로 설정합니다.
각 서비스는 원본 JWT 검증 및 자기 데이터의 소유권 검사를 유지해야 합니다.
인증 실패는 401 `COMMON_002`, 권한 부족은 403 `COMMON_004`로 응답합니다.
공개 API도 잘못된 토큰을 보내면 401입니다(기존 필터보다 엄격한 README 요구사항).

CORS는 `SecurityConfig`에서 한 번만 설정합니다. `http://localhost:5173`만 허용하고
Authorization/Content-Type 헤더 및 credentials를 허용합니다.
정상 preflight는 토큰이나 Redis 조회 없이 처리합니다.
헬스 경로만 비로그인 허용하며 나머지 Actuator 경로는 기본 인증 정책을 따릅니다.

인증 테스트: 서비스 디렉터리에서 `.\gradlew.bat test`
테스트는 Redis 조회를 대체하므로 외부 서버 없이 실행할 수 있습니다.
