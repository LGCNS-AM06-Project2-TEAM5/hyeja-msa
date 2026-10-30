# gateway-service

Spring Cloud Gateway입니다. 프런트는 이 서비스(8000)만 호출합니다.

## 할 일

- 경로 기반 라우팅 (프런트가 쓰는 경로를 그대로 유지)

| 경로 | 보낼 서비스 |
|---|---|
| `/api/members/**`, `/api/regions` | member-service |
| `/api/policies/**`, `/api/terms/**` | policy-service |
| `/api/favorite/**` | favorite-service |
| `/api/notification/**` | notification-service |

- JWT 검증: 서명 확인, Redis 로그아웃 블랙리스트 확인, 통과하면 회원 ID·권한을 헤더로 전달
- 로그인 없이 허용하는 경로와 관리자 전용 경로(`POST /api/policies/sync`, `/api/notification/admin/**`)는 hyeja-backend의 `SecurityConfig`를 그대로 옮깁니다.
- CORS: `http://localhost:5173` 허용 (지금은 hyeja-backend의 `SecurityConfig`에 있음)
- 공통 필터: 요청 로깅

참고 코드: `ljw2869/msa`의 `apigateway-service` (`AuthorizationHeaderFilter`, `LoggingFilter`, `GlobalFilter`)
