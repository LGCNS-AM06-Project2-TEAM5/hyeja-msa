# Docker Compose 통합 실행

저장소 루트의 `compose.yaml`을 사용합니다. Docker Desktop의 Linux containers 모드와
Compose v2 이상, 이미지/Gradle 의존성/GitHub 접근이 가능한 네트워크가 필요합니다.
기존 IntelliJ에서 실행한 8000/8761/8888 서버는 종료하거나 호스트 포트를 변경하세요.
7개 JVM과 기반 시설을 함께 실행하므로 Docker에 충분한 메모리를 할당하세요(8GB 이상 권장).

## 환경변수

루트 `.env`가 없을 때만 `.env.example`을 복사합니다. 기존 `.env`는 덮어쓰지 않습니다.
`docker/.env.example`을 참고해 다음 필수 값을 루트 `.env`에 추가하세요.

| 변수 | 용도 |
|---|---|
| `DB_ROOT_PASSWORD` | Compose MariaDB root 비밀번호 |
| `USER_DB_PASSWORD` | user_app / user_db |
| `POLICY_DB_PASSWORD` | policy_app / policy_db |
| `ENGAGEMENT_DB_PASSWORD` | engagement_app / engagement_db |
| `JWT_SECRET` | User와 Gateway가 공유하는 최소 32바이트 UTF-8 키 |
| `CONFIG_ENCRYPT_KEY` | Config Server 전용 암호화 마스터 키. JWT 키와 다른 무작위 값, 최소 32 UTF-8 바이트 |
| `CONFIG_GATEWAY_PASSWORD` 등 5개 서비스 비밀번호 | 서비스별 Config 조회 계정. 각 값은 서로 다르게 16자 이상·72 UTF-8 바이트 이하 |
| `CONFIG_ADMIN_PASSWORD` | 암·복호화 관리자 비밀번호. 읽기 비밀번호와 다른 값, 16자 이상·72 UTF-8 바이트 이하 |
| `GATEWAY_ENGAGEMENT_SECRET` | Gateway·engagement 전용 내부 JWT 키. JWT_SECRET과 다른 무작위 값, 최소 32 UTF-8 바이트 |

비밀번호는 서로 다르게 설정합니다. 실제 비밀값은 커밋하지 않습니다.
Config Server는 HTTPS만 제공하고 서비스별 Basic 계정으로 자기 JSON 설정만 조회할 수 있습니다. 먼저 `docker/New-ConfigTls.ps1`로 개발 인증서를 생성하고 .env에 CONFIG_TLS_KEY_STORE_PASSWORD/CONFIG_TLS_TRUST_STORE_PASSWORD를 설정합니다. 인증서 검증은 끄지 않습니다. Compose는 서비스별 읽기 자격증명과 공개 CA truststore만 각 클라이언트에 전달하고, 마스터 키와 관리자 비밀번호·서버 개인키는 Config Server에만 전달합니다. 암호화·복호화와 설정 이관 방법은 [Config Server 문서](../infra/config-service/README.md)를 참고하세요. 클라이언트 초기 접속 비밀번호는 Config Git이 아닌 환경변수로 관리합니다.
Compose는 필요한 변수만 서비스에 명시적으로 전달합니다. 전체 `.env`를 주입하지 않아
기존 `SERVER_PORT=8080`, `DB_NAME=hyeja`, `DB_USERNAME=root`, `REDIS_HOST=localhost`가
컨테이너 설정을 덮어쓰지 않습니다. 관리자 계정은 주입하지 않습니다.
외부 API/메일 키는 필요한 비즈니스 서비스에만 전달합니다.

user-service는 docker 프로필에서 `application-dev.yml`을 읽지 않으므로 Compose가 SMTP host·port 및 인증/TLS 설정을 전달합니다. `MAIL_HOST` 기본값은 smtp.gmail.com, `MAIL_PORT` 기본값은 587입니다. 실제 인증 메일 발송에는 `MAIL_USERNAME`·`MAIL_PASSWORD`가 필요합니다. 로컬 Compose의 health check는 외부 SMTP의 가용성·자격증명에 의존하지 않도록 mail health indicator를 끕니다. 메일 발송 자체를 끄거나 발송 실패를 성공으로 처리하지는 않습니다.

## 실행

모든 명령은 저장소 루트에서 실행합니다.

```powershell
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 600
docker compose ps -a
```

Dockerfile은 Java 17로 `bootJar`를 빌드하고 JRE 17 이미지에서 일반 사용자로 실행합니다.
호스트 JDK/빌드 산출물은 필요하지 않습니다. 테스트는 별도로 `gradlew test`로 실행합니다.
`kafka-init`이 토픽 생성 후 종료 코드 0으로 종료되는 것은 정상입니다.

기반 시설만 먼저 실행할 수도 있습니다.

```powershell
docker compose up -d --wait mariadb redis kafka zipkin
docker compose up -d kafka-init
powershell -File docker/Test-Compose.ps1 -InfrastructureOnly
```

## 접속 주소

| 구성 | PC에서 접근 | 컨테이너끼리 접근 |
|---|---|---|
| MariaDB | localhost:13306 | mariadb:3306 |
| Redis | localhost:16379 | redis:6379 |
| Kafka | localhost:19092 | kafka:9092 |
| Zipkin | http://localhost:9411 | http://zipkin:9411 |
| Discovery | http://localhost:8761 | http://discovery-service:8761 |
| Config | https://localhost:8888 | https://config-service:8888 |
| Gateway | http://localhost:8000 | http://gateway-service:8000 |

호스트 포트는 `.env`에서 변경할 수 있습니다. 비즈니스 서비스의 8081~8084는
컨테이너 네트워크에만 열려 있습니다. DB/Redis 호스트 포트는 기존 로컬 인스턴스와
충돌하지 않도록 분리했습니다. PC에서 실행하는 서비스가 Compose 기반 시설에 붙을 때는
호스트 포트를 사용하세요. Kafka 외부 리스너는 같은 PC의 localhost 접속용입니다.

## 준비 순서와 설정

- MariaDB는 볼륨 최초 생성 시 DB 3개와 전용 계정만 만듭니다. 테이블/업무 데이터는 만들지 않습니다.
- 각 계정은 자기 DB에만 권한이 있습니다. Recommendation은 DB가 없습니다.
- Kafka는 단일 노드 KRaft이며 `member-registered`, `policy-updated`, `notification-created`
  토픽을 생성합니다. 기존 토픽은 유지합니다. 개발용 단일 복제본 구성입니다.
- Discovery가 준비되면 Config를 실행하고, Config/DB/Redis/Kafka 준비 후 서비스들을 실행합니다.
- `SPRING_CONFIG_IMPORT`와 `CONFIG_SERVER_URL`을 함께 지정합니다. 파일에 선언된
  import도 처리될 수 있어 환경변수 한 개로 덮어썼다고 가정하지 않습니다.
  서비스 파일의 import도 `CONFIG_SERVER_URL`을 참조하여 localhost 접속을 방지합니다.
- Config Git 주소/브랜치는 `CONFIG_GIT_URI`, `CONFIG_GIT_LABEL`로 변경할 수 있습니다.
- 원격 `hyeja-config`는 수정하지 않습니다. 컨테이너 접속 주소는 환경변수로 우선 적용합니다.
- 7개 애플리케이션에 Boot 4 Zipkin starter와 수집 주소/100% 샘플링을 설정했습니다.
  Zipkin은 개발용 메모리 저장소로 재시작 시 트레이스가 사라질 수 있습니다.

현재 비즈니스 서비스는 `/health-check` 골격입니다. DB/Kafka 접속 환경은 제공하지만,
서비스 코드에 JDBC/JPA, Redis, Kafka 의존성이나 실제 처리가 없는 경우 해당 연결이
자동 구현되지는 않습니다. 담당자가 기능 추가 시 전달된 설정을 사용하면 됩니다.
DB 계정 및 Kafka 송수신은 아래 스모크 테스트로 별도 검증합니다.

## Gateway 전체 응답 확인

```powershell
powershell -File docker/Test-Compose.ps1
```

다음을 순서대로 확인합니다.

1. DB 계정 접속 및 타 DB 접근 거부
2. Redis PING
3. 임시 Kafka 토픽에서 메시지 발행/수신 후 토픽 삭제
4. Config Git 설정 조회, Eureka의 6개 Client UP 등록
5. Gateway를 통한 서비스 4개 `/health-check` 응답
6. Zipkin에서 Policy 서비스 트레이스 수신

스크립트는 실행 중인 Gateway 키로 5분짜리 일반 회원 테스트 JWT를 메모리에서 생성합니다.
키/토큰을 출력하거나 파일로 저장하지 않으며 실제 회원/관리자 계정을 생성하지 않습니다.
이는 인증된 라우팅 검증용이며 실제 로그인/로그아웃 테스트를 대체하지 않습니다.
Eureka 등록/캐시 반영에는 시간이 걸리므로 초기 503이면 잠시 후 다시 실행하세요.

Postman에서는 아래 Docker 전용 경로를 정상 Bearer Token으로 호출할 수 있습니다.

| GET 경로 | 예상 응답 |
|---|---|
| `/_infra/health/user` | user-service OK |
| `/_infra/health/policy` | policy-service OK |
| `/_infra/health/engagement` | engagement-service OK |
| `/_infra/health/recommendation` | recommendation-service OK |

앞에 `http://localhost:8000`을 붙입니다. 토큰 없이는 401입니다.
Docker 프로필에서만 활성화되며 인증 정책을 바꾸거나 공개 예외를 추가하지 않습니다.
기존 업무 라우팅도 유지합니다. `/actuator/health`는 Gateway 자신의 상태입니다.
Zipkin UI에서 Run Query로 서비스 트레이스를 확인할 수 있습니다.

## GHCR 이미지로 배포

develop에 머지되면 CD가 서비스 7개 이미지를 GHCR에 올립니다.
`compose.deploy.yaml`은 `compose.yaml` 위에 이미지 이름만 덮어써서, 소스를 빌드하지 않고 GHCR 이미지로 실행합니다.

```bash
./scripts/deploy.sh
```

스크립트는 이미지 받기(pull) → 전체 기동(up -d --wait) → Gateway를 통한 서비스 4개 `/health-check` 확인 순서로 실행하고,
4개 모두 `PASS`면 성공입니다. 헬스 체크용 토큰은 `Test-Compose.ps1`과 같은 방식으로 메모리에서만 만듭니다.

| 실행 방법 | 용도 |
|---|---|
| `./scripts/deploy.sh` | develop 태그 이미지로 배포 |
| `IMAGE_TAG=<커밋 SHA> ./scripts/deploy.sh` | 특정 버전으로 배포하거나 되돌리기 |
| `SKIP_PULL=1 ./scripts/deploy.sh` | pull 없이 로컬 이미지로 확인 |

GHCR 패키지가 비공개면 pull 전에 `read:packages` 권한이 있는 GitHub 토큰으로 `docker login ghcr.io`를 먼저 합니다.
CD 이미지가 올라오기 전에는 `docker compose -f compose.yaml -f compose.deploy.yaml build`로 같은 이름의 이미지를 로컬에서 만들어 확인할 수 있습니다.


## 종료와 데이터 유지

```powershell
docker compose down
```

기본 종료는 DB/Redis/Kafka 볼륨을 유지합니다. `down -v`는 데이터를 삭제하므로
데이터를 버리기로 결정한 경우에만 사용하세요. 초기화 스크립트는 빈 DB 볼륨에서만
실행되므로 기존 볼륨의 비밀번호는 `.env` 수정만으로 바뀌지 않습니다.
비밀번호 변경 시 DB 계정과 Compose 환경변수를 함께 맞춰야 합니다.

로그 확인: `docker compose logs --tail 100 서비스이름`.
전체 `docker compose config` 출력에는 비밀값이 포함될 수 있어 공유하지 마세요.
구문 확인에는 `docker compose config --quiet`를 사용합니다.

## 완료 조건 검증

Compose 구문/애플리케이션 빌드와 실제 컨테이너 실행은 별도입니다.
`docker compose up --wait`와 `Test-Compose.ps1`이 모두 성공한 뒤 이슈 #8의
전체 기동/연결 완료 조건을 체크하세요. 업무 API 정상 응답은 각 기능 구현 후 추가 검증합니다.
