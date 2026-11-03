# Gateway → engagement 내부 인증

## 동작

사용자 JWT와 로그아웃 블랙리스트는 Gateway에서 검증한다. 검증에 성공하면 관심·알림 경로(`/api/favorite`, `/api/notification` 및 하위 경로)에만 별도 HS256 내부 JWT를 발급하여 `X-Gateway-Assertion`에 넣는다. 외부에서 주입한 이 헤더는 제거한 후 새 토큰으로 대체한다. 다른 서비스에는 내부 토큰을 전달하지 않으며 기존 Authorization 및 검증된 회원 헤더 전달 방식을 유지한다.

engagement는 내부 JWT의 서명, HS256 알고리즘, `iss=gateway-service`, `aud=[engagement-service]`, 양수 회원 ID인 `sub`, 필수 `iat`/`exp`를 검증한다. 토큰 수명은 120초이며 미래 iat는 최대 5초까지만 허용하고, 만료 시간의 추가 유예는 없다. 검증된 sub를 Spring Security principal(Long)로 설정한다. Controller는 @AuthenticationPrincipal로만 회원을 식별하며 X-Member-Id·X-Member-Role을 신뢰하지 않는다.

내부 토큰이 없거나 유효하지 않으면 401 COMMON_002를 반환한다. 사용자 JWT만으로 engagement를 직접 호출해도 인증되지 않는다. 인증은 세션 없이 동작한다. `/health-check`, `/actuator/health` 및 하위 health 경로는 Docker 상태 점검을 위해 인증 없이 접근 가능하며, 나머지 경로에는 인증이 필요하다. `/api/health`도 공통 health 경로로 허용하되 현재 별도 controller는 없다.

## 실행 설정

Gateway와 engagement 두 프로세스에만 **동일한** `GATEWAY_ENGAGEMENT_SECRET`을 주입한다. 이는 사용자 JWT의 JWT_SECRET과 다른 무작위 키여야 하며 최소 32 UTF-8 바이트여야 한다. 문자열을 UTF-8 바이트로 사용하며 Base64 디코딩하지 않는다. compose.yaml은 두 서비스에만 이 변수를 전달한다.

키 누락·공백·길이 부족 시 기동에 실패한다. Gateway는 사용자 JWT 키와 내부 키가 동일한 경우에도 기동을 거부한다. 프로덕션 기본키나 인증을 생략하는 테스트 프로필은 없다. 자동화 테스트의 고정 키는 src/test에서만 사용한다.

현재 환경변수로 키를 주입하며 실키는 Git에 기록하지 않는다. Config Server의 `{cipher}` 설정 암호화와 무중단 키 교체는 별도 작업이다. 양쪽 키가 다르면 요청은 인증 실패한다. 이번 구현의 단일 키를 교체할 때는 두 서비스의 배포 순서 및 일시적인 인증 실패를 고려해야 한다.

## 범위와 한계

사용자 토큰 발급·로그인과 user/policy/recommendation 코드는 수정하지 않는다. Feign·Kafka 계약도 추가하지 않는다. 관심·알림의 비즈니스 로직과 저장 데이터는 동일하다.

대칭키 보유자는 내부 토큰 발급도 가능하다. 내부 토큰은 bearer 자격증명으로 만료 전 재사용 가능하며 별도 재사용 방지 저장소는 두지 않는다. 클라이언트에 노출하거나 로그에 남기지 않는다. Gateway→engagement TLS와 네트워크 접근 제한은 운영 인프라에서 적용해야 한다. 호스트 포트 미공개만으로 동일 네트워크 컨테이너의 접근까지 차단되지는 않는다.

향후 Feign 내부 API도 이번 인증 필터의 예외로 열지 않는다. 별도의 서비스 인증 계약을 합의한 후 구현한다. engagement는 Redis를 직접 조회하지 않으며 사용자가 로그아웃해도 이미 발급된 내부 토큰은 최대 2분 동안 유효하다. 새로운 요청의 블랙리스트 확인은 Gateway가 계속 담당한다.

## 테스트

```powershell
./infra/gateway-service/gradlew.bat -p infra/gateway-service test --no-daemon
./services/engagement-service/gradlew.bat -p services/engagement-service test --no-daemon
```

Gateway: 사용자 인증·Redis 실패 차단, 헤더 위조 제거, engagement 전용 토큰 발급과 120초 수명, 기존 다른 서비스 동작, 잘못된 키 설정에 의한 기동 실패를 검증한다.

engagement: 헤더만 주입한 접근, 사용자 Authorization만 전달한 직접 접근, 잘못된 서명·발급자·수신 대상·만료·필수 claim·수명·중복 헤더를 차단한다. 토큰 회원과 조작한 헤더가 다를 때 토큰 회원으로만 관심·알림에 접근하는지 및 요청 간 principal이 남지 않는지 확인한다. 실제 Compose와 TLS 배포 검증은 별도로 수행한다.
