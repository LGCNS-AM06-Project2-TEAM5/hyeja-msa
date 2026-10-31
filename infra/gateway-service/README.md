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

## 인증 계획 (아직 구현하지 않음, hyeja-backend `SecurityConfig` 기준)

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
현재 포트, 라우팅과 CORS는 Gateway의 로컬 `application.yml`에 있습니다.
원격 관리로 전환할 때 해당 항목을 `hyeja-config/gateway-service.yml`로 옮기고
Gateway를 재시작하면 됩니다. 원격 파일은 이 작업에서 변경하지 않습니다.

원래 API 경로를 변경하지 않고 전달합니다. 대상 서비스가 Eureka에 등록되어
있지 않으면 해당 경로 요청은 503으로 응답할 수 있습니다.
JWT 인증, Redis 블랙리스트와 회원 정보 헤더 전달은 후속 작업입니다.
