# config-service

Spring Cloud Config Server. 포트 8888, 설정 원본은 별도 Git 저장소 hyeja-config다. `{cipher}` 값은 서버에서 복호화한 후 인증된 클라이언트에 제공한다.

## 필수 환경변수

| 변수 | 주입 대상 | 설명 |
|---|---|---|
| CONFIG_ENCRYPT_KEY | Config Server만 | 최소 32 UTF-8 바이트의 별도 무작위 암호화 마스터 키 |
| CONFIG_READER_USERNAME | 서버·클라이언트 | 기본 config-reader |
| CONFIG_READER_PASSWORD | 서버·클라이언트 | 읽기 비밀번호, 16자 이상·72 UTF-8 바이트 이하 |
| CONFIG_ADMIN_USERNAME | Config Server·운영자 | 기본 config-admin |
| CONFIG_ADMIN_PASSWORD | Config Server·운영자 | 읽기 계정과 다른 비밀번호, 16자 이상·72 UTF-8 바이트 이하 |

마스터 키·JWT 키·계정 비밀번호는 서로 다르게 설정한다. 서버의 bootstrap 비밀값은 Config Git이 아닌 환경변수나 배포 Secret으로 주입한다. 이 서버가 시작할 때 필요한 값 자체를 `{cipher}`로 설정하면 안 된다. 설정 누락·공백·짧은 키·동일 계정/비밀번호는 기동 시 거절한다. 실제 값은 로그나 Git에 기록하지 않는다.

루트 .env는 Compose가 읽는다. IntelliJ나 PowerShell에서 Java를 직접 실행하면 .env를 자동으로 읽지 않으므로 Run Configuration 또는 프로세스 환경변수에 주입한다.

```powershell
./infra/config-service/gradlew.bat -p infra/config-service bootRun
```

Git URL과 브랜치는 SPRING_CLOUD_CONFIG_SERVER_GIT_URI, SPRING_CLOUD_CONFIG_SERVER_GIT_DEFAULT_LABEL로 변경 가능하다. 시작 시 clone하므로 Git 연결이 필요하다. Eureka 주소는 EUREKA_SERVER_URL(기본 http://localhost:8761/eureka/)이다.

## 접근 권한

| 요청 | 익명 | 읽기 계정 | 관리자 |
|---|---|---|---|
| GET /actuator/health 및 health 하위 경로 | 허용 | 허용 | 허용 |
| GET /{application}/{profile}[/{label}] 및 YAML/properties | 401 | 허용 | 허용 |
| POST /encrypt, /decrypt 및 /{application}/{profiles} 변형 | 401 | 403 | 허용 |
| GET /encrypt/status | 401 | 403 | 허용 |
| 다른 Actuator·refresh·monitor | 차단 | 차단 | 차단 |

HTTP Basic과 무상태 인증을 사용하며 health 응답에 세부 설정을 노출하지 않는다. 읽기 계정도 복호화된 비밀값을 받을 수 있다. 현재 팀 공통 계정이며 서비스별 설정 접근 분리는 구현하지 않았다. 운영에서는 내부 네트워크 제한과 HTTPS를 적용한다. Basic 인증 및 `{cipher}`는 전송 구간을 암호화하지 않는다.

## Postman으로 암호화

1. POST http://localhost:8888/encrypt
2. Authorization → Basic Auth → CONFIG_ADMIN_USERNAME/CONFIG_ADMIN_PASSWORD 입력
3. Body → raw → Text, Content-Type: text/plain; charset=utf-8
4. Body에 암호화할 실제 값만 입력. `${GATEWAY_ENGAGEMENT_SECRET}` 같은 참조 문자열을 암호화하지 않는다.
5. 응답 암호문 앞에 `{cipher}`를 붙여 Config Git의 YAML에 저장한다. YAML에서는 전체 값을 따옴표로 감싼다.

예시(placeholder를 실제 결과로 교체해야 함):

```yaml
# gateway-service.yml 및 engagement-service.yml에 동일한 내부 JWT 키를 암호화해 넣는다.
internal-auth:
  secret: '{cipher}<POST /encrypt 결과로 교체>'
```

내부 JWT 키를 공통 application.yml에 두면 모든 서비스에 전달되므로 서비스별 파일에만 둔다. 같은 평문도 암호화할 때마다 암호문이 달라질 수 있다.

확인용 POST /decrypt는 관리자 Basic Auth와 text/plain Body를 사용하며 `{cipher}` 접두사 없이 암호문만 넣는다. 설정 조회는 읽기 계정으로 GET /gateway-service/docker, GET /engagement-service/docker를 호출한다. 복호화 응답과 설정 응답은 평문을 포함하므로 로그·화면·PR에 공유하지 않는다.

## 클라이언트 연결

Compose는 클라이언트에 다음 표준 환경변수를 전달하므로 서비스 Java 코드 변경은 필요 없다.

```text
SPRING_CLOUD_CONFIG_USERNAME=<읽기 계정 이름>
SPRING_CLOUD_CONFIG_PASSWORD=<읽기 계정 비밀번호>
```

IntelliJ로 실행하는 클라이언트도 같은 변수를 설정한다. 최초 접속에 필요한 비밀번호를 Config Git에서 가져올 수는 없으므로 외부 주입한다. 관리자 비밀번호와 마스터 키는 클라이언트에 전달하지 않는다.

민감 설정을 반드시 원격에서 받아야 하는 실행은 SPRING_CONFIG_IMPORT=configserver:http://localhost:8888처럼 optional 없는 import를 사용한다. Compose는 이미 mandatory import다. 기존 로컬 optional import를 유지하면 서버 장애 시 로컬 값으로 기동할 수 있으므로 이관 완료 환경에서는 제거해야 한다.

서버는 복호화 실패를 invalid.*로 바꿔 일부 설정만 반환하지 않고 HTTP 500으로 거절한다. mandatory Config Client는 이때 기동에 실패한다. 정상 복호화 이후에도 INTERNAL_AUTH_SECRET 등 동일 프로퍼티에 대응하는 환경변수는 원격 값보다 우선하므로 중복 주입을 제거한다. `${GATEWAY_ENGAGEMENT_SECRET}` 같은 로컬 참조와 원격 internal-auth.secret의 최종 적용값도 확인한다.

## 적용 상태와 후속 작업

이 브랜치는 Config Server 코드·접근 제어·Compose 연결·이관 절차를 제공한다. 외부 hyeja-config 저장소의 실키 암호문은 자동 생성/커밋하지 않았다. 실제 마스터 키를 설정하고 운영자가 암호화하여 반영해야 한다. 샘플값을 실키로 사용하지 않는다.

#54 내부 JWT 변경을 반영했다. 실제 Config Git의 암호문을 적용한 뒤 Gateway·engagement의 실키 이관을 검증한다. 사용자 JWT·DB·메일 비밀번호의 추가 이관은 각 담당자와 협의한다.

마스터 키를 잃으면 기존 암호문을 복호화할 수 없다. Secret 관리 시스템에 백업하고 접근자를 제한한다. 교체 시 기존 키로 복호화 → 새 키로 재암호화 → Config Git 변경 및 재기동을 조율한다. 단일 키 방식이며 자동 순환·무중단 교체·Bus/refresh는 구현하지 않았다. Git 이력에 평문이 있었다면 암호문으로 덮어쓰는 것만으로 해결되지 않으므로 해당 비밀값을 교체한다.

## 테스트

```powershell
./infra/config-service/gradlew.bat -p infra/config-service test bootJar --no-daemon
```

임시 native 설정 저장소와 테스트 전용 키로 실제 Config Server/Client HTTP 통신을 검증한다. 암·복호화 왕복, 역할별 접근 제어, 설정 조회·설정 우선순위, 잘못된 암호문과 mandatory client 실패, bootstrap 비밀값 누락/재사용을 확인한다. GitHub·Eureka·Docker가 필요 없다.
