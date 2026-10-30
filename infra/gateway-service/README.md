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

## 인증 (hyeja-backend `SecurityConfig` 기준)

- JWT 서명 확인과 Redis 로그아웃 블랙리스트 확인 후, 회원 ID·권한을 헤더로 각 서비스에 전달합니다.
- 로그인 없이 허용: `POST /api/members`, `/api/members/login`, `/api/members/email-verifications/**`, `GET /api/members/find-email`, `/api/regions`, `/api/terms/**`, `/api/policies/housing`, `/api/policies/card-news/guest`, `/api/policies/card-detail/*`, `/api/policies/{policyId}`, `/api/health`
- 로그인 필요(위 허용 경로보다 우선): `GET /api/policies/search`, `GET /api/policies/card-news`
- 관리자 전용: `POST /api/policies/sync`, `/api/notification/admin/**`
- 로그인 없이 허용된 경로라도 **잘못된 토큰을 보내면** 지금처럼 오류를 돌려줘야 합니다(정책 상세 등). 각 서비스에서도 필요한 권한을 다시 확인합니다.
- CORS: `http://localhost:5173` 허용

참고 코드: `ljw2869/msa`의 `apigateway-service`
