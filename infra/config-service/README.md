# Config Server: 암호화·HTTPS·서비스별 조회 권한

Config Server는 포트 8888에서 **HTTPS만** 제공한다. Git의 `{cipher}` 값을 복호화하고 Basic 인증된 계정에 허용된 설정만 반환한다. HTTP listener를 추가하지 않으며 server.ssl.enabled=false 설정은 기동 시 거절한다.

## 1. 루트 .env에 설정

실제 값은 Git에 올리지 않는다. .env.example은 형식만 참고하고 기존 .env를 덮어쓰지 않는다.

| 변수 | 대상 | 조건 |
|---|---|---|
| CONFIG_ENCRYPT_KEY | Config Server만 | JWT 키와 다른 무작위 마스터 키, 최소 32 UTF-8 바이트 |
| CONFIG_ADMIN_USERNAME | Config Server·운영자 | 기본 config-admin |
| CONFIG_ADMIN_PASSWORD | Config Server·운영자 | 관리자 비밀번호 |
| CONFIG_GATEWAY_PASSWORD | 서버·Gateway | Gateway 전용 읽기 비밀번호 |
| CONFIG_USER_PASSWORD | 서버·User | User 전용 읽기 비밀번호 |
| CONFIG_POLICY_PASSWORD | 서버·Policy | Policy 전용 읽기 비밀번호 |
| CONFIG_ENGAGEMENT_PASSWORD | 서버·Engagement | Engagement 전용 읽기 비밀번호 |
| CONFIG_RECOMMENDATION_PASSWORD | 서버·Recommendation | Recommendation 전용 읽기 비밀번호 |
| CONFIG_TLS_KEY_STORE_PASSWORD | 서버·인증서 생성 도구 | CA/server PKCS12 비밀번호, 생성 도구 기준 12자 이상 |
| CONFIG_TLS_TRUST_STORE_PASSWORD | 클라이언트·인증서 생성 도구 | 공개 CA truststore 비밀번호, 생성 도구 기준 12자 이상 |

관리자와 5개 서비스의 계정 비밀번호는 모두 서로 달라야 하고 각각 16자 이상·72 UTF-8 바이트 이하여야 한다. 키·비밀번호를 재사용하지 않는다. 기존 CONFIG_READER_USERNAME/PASSWORD는 더 이상 사용하지 않는다.

기본 서비스 사용자 이름은 config-gateway, config-user, config-policy, config-engagement, config-recommendation이다. 필요하면 각각 CONFIG_GATEWAY_USERNAME 등으로 변경한다. 서버와 해당 클라이언트에 동일하게 적용해야 한다.

마스터 키·Config 인증 정보·TLS 저장소 비밀번호는 Config Git이 아닌 환경변수/배포 Secret에 둔다. 초기 접속에 필요한 비밀값 자체를 Config Server에서 가져올 수는 없다. .env는 Compose가 읽으며 Java 직접 실행 시 자동으로 읽지 않는다.

## 2. 개발용 CA·인증서 생성

Java 17의 keytool이 PATH에 있어야 한다. 저장소 루트에서 실행한다.

```powershell
powershell -ExecutionPolicy Bypass -File docker/New-ConfigTls.ps1
```

프로세스 환경변수에 TLS 저장소 비밀번호가 없으면 안전한 입력 프롬프트가 표시된다. .env에 지정한 두 저장소 비밀번호와 같은 값을 각각 입력한다. 스크립트는 .env를 읽거나 변경하지 않는다.

생성 파일:

- docker/config-tls/private/ca.p12: CA 개인키. 호스트에서만 보관하고 접근 권한을 제한한다.
- docker/config-tls/server/server.p12: Config Server 인증서와 개인키. 서버에만 읽기 전용 마운트한다.
- docker/config-tls/trust/ca.crt: 공개 CA 인증서. Postman과 curl에서 신뢰한다.
- docker/config-tls/trust/truststore.p12: 공개 CA만 포함한 클라이언트 신뢰 저장소.

SAN은 config-service, localhost, 127.0.0.1이다. 서버 인증서는 365일, 개발 CA는 3650일 유효하다. 디렉터리는 .gitignore와 .dockerignore로 제외된다. 생성 도구는 기존 저장소를 덮어쓰지 않는다. CA와 서버 개인키를 클라이언트에 전달하지 않는다. 운영에서는 운영 CA로 발급한 인증서와 접근이 제한된 Secret을 사용하고 만료 전 교체한다. 다른 호스트 이름을 쓰면 해당 SAN이 포함된 인증서를 별도 발급한다.

## 3. Compose 실행

```powershell
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 600
```

모든 Config Client는 configserver:https://config-service:8888로 연결하고 CA truststore 검증 및 호스트 이름 검증을 사용한다. 인증서 검증을 끄는 옵션은 없다. 서비스의 업무 HTTP 포트는 기존대로이며 Config 연결만 HTTPS다.

Config Server health check는 curl --cacert로 HTTPS를 검증한다. 각 서비스는 자기 계정과 SPRING_CLOUD_CONFIG_NAME을 받아 초기 default 프로필 조회부터 자기 이름을 사용한다. Config Server의 마스터 키·관리자 비밀번호는 클라이언트에 주입하지 않는다.

IntelliJ 실행도 HTTPS를 사용한다. Config Server에는 CONFIG_TLS_KEY_STORE=file:C:/.../docker/config-tls/server/server.p12와 저장소 비밀번호를 추가한다. 클라이언트에는 아래 표준 환경변수를 지정한다(예: Gateway).

```text
CONFIG_SERVER_URL=https://localhost:8888
SPRING_CONFIG_IMPORT=configserver:https://localhost:8888
SPRING_CLOUD_CONFIG_URI=https://localhost:8888
SPRING_CLOUD_CONFIG_NAME=gateway-service
SPRING_CLOUD_CONFIG_USERNAME=config-gateway
SPRING_CLOUD_CONFIG_PASSWORD=<Gateway 전용 비밀번호>
SPRING_CLOUD_CONFIG_TLS_ENABLED=true
SPRING_CLOUD_CONFIG_TLS_TRUST_STORE=file:C:/.../docker/config-tls/trust/truststore.p12
SPRING_CLOUD_CONFIG_TLS_TRUST_STORE_TYPE=PKCS12
SPRING_CLOUD_CONFIG_TLS_TRUST_STORE_PASSWORD=<truststore 비밀번호>
```

서비스별 애플리케이션 이름·사용자 이름·비밀번호는 바꿔 적용한다. 원격 설정이 필수인 실행에서는 optional import를 사용하지 않는다. 기존 optional 로컬 프로필은 원격 실패 시 로컬 값으로 기동할 수 있다.

## 4. 조회 권한

| 읽기 계정 | 허용 애플리케이션 |
|---|---|
| config-gateway | gateway-service |
| config-user | user-service |
| config-policy | policy-service |
| config-engagement | engagement-service |
| config-recommendation | recommendation-service |

서비스 계정에는 GET /{자기 application}/{profiles}[/{허용 label}] 형태의 JSON Environment 조회만 허용한다. 기본 허용 프로필은 default,local,dev,docker이며 각 프로필을 쉼표로 조합할 수 있다. 명시적 label은 main만 허용한다. 배포 환경에 맞게 config-security.allowed-profiles/allowed-labels를 서버 설정에서 변경한다. prod나 다른 label은 명시적 허용 전에는 거절한다.

다른 서비스, application의 직접 조회, 다중 애플리케이션 이름, YAML·JSON 파일·properties 렌더링, raw 파일 다운로드, 인코딩 우회 경로는 서비스 계정에서 차단한다. 관리자만 전체 설정 조회와 /encrypt·/decrypt·/encrypt/status에 접근할 수 있다. health만 익명 허용하고 다른 Actuator 및 refresh/monitor는 차단한다.

공통 application.yml은 각 서비스 설정에 합쳐진다. 따라서 서비스별 비밀값은 공통 파일에 넣지 않는다. 이 권한 분리가 공통 설정에 잘못 넣은 비밀값까지 자동 분리하는 것은 아니다.

## 5. Postman 검증

Settings → Certificates → CA Certificates에 docker/config-tls/trust/ca.crt를 등록하고 SSL certificate verification은 켜 둔다. CA 인증서는 개인키가 없는 공개 파일이다. Postman 버전에 따라 Custom CA Certificates를 활성화한다.

- GET https://localhost:8888/actuator/health: No Auth → 200
- POST https://localhost:8888/encrypt: 관리자 Basic Auth, raw Text/plain Body → 200 암호문
- POST https://localhost:8888/decrypt: 관리자, 접두사 없는 암호문 Body → 200 원래 값
- GET https://localhost:8888/gateway-service/docker: Gateway 계정 → 200
- 같은 Gateway 계정으로 /user-service/docker, /application/default, /gateway-service-docker.yml → 403
- 같은 조회에 No Auth → 401
- Gateway 계정으로 /encrypt·/decrypt → 403
- 승인되지 않은 CA나 호스트 이름으로 요청 → 인증서 검증 실패
- http://localhost:8888은 정상 설정 응답을 제공하지 않음

키를 암호화할 때 참조 문자열 `${GATEWAY_ENGAGEMENT_SECRET}` 대신 실제 값을 Body에 넣는다. 암호문을 저장할 때만 접두사를 붙인다.

```yaml
# 별도 hyeja-config 저장소의 gateway-service.yml 및 engagement-service.yml
internal-auth:
  secret: '{cipher}<POST /encrypt 결과>'
```

동일한 내부 JWT 키를 양쪽에 제공하되 공통 application.yml에는 넣지 않는다. 복호화/설정 응답에는 평문이 있으므로 화면·로그·PR에 공유하지 않는다. Config Git의 실키 암호문은 이 브랜치에서 자동 변경하지 않았다.

## 6. 실패·키 관리

복호화 실패는 일부 기본값으로 넘어가지 않도록 설정 응답 전체를 500으로 거절한다. mandatory client는 이 경우 기동 실패한다. 암호화 마스터 키·계정 비밀번호·TLS 파일이 누락되거나 올바르지 않으면 정상 기동할 수 없다. INTERNAL_AUTH_SECRET처럼 같은 프로퍼티 이름의 환경변수는 원격 값보다 우선하므로 이관 후 중복 주입을 정리한다.

암호화키를 잃으면 기존 암호문을 복호화할 수 없다. 보안 저장소에 백업하고 접근자를 제한한다. TLS CA/인증서 교체, 비밀번호 교체, 암호문 재암호화는 서버·클라이언트 재기동 순서를 조율한다. 현재 자동 순환·무중단 교체·Bus refresh는 구현하지 않았다. Git 이력에 평문 비밀값이 있었다면 해당 값을 교체한다.

## 7. 테스트

```powershell
./infra/config-service/gradlew.bat -p infra/config-service test bootJar --no-daemon
```

임시 native 저장소와 JDK가 생성한 일회성 인증서로 실제 HTTPS Config Server/Client를 검증한다. 5개 계정의 정상 로딩, 교차 서비스·우회 경로 차단, 신뢰하지 않는 인증서·호스트 이름 거부, 복호화 실패·기동 실패·설정 우선순위 및 Compose의 TLS/계정 연결을 확인한다. 테스트 개인키는 Git에 저장하지 않는다. 실제 Docker 배포 검증은 별도로 실행한다.