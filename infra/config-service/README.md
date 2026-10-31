# config-service

담당: 이지원

Spring Cloud Config Server입니다. 설정 파일은 별도 저장소 `hyeja-config`에서 읽습니다.

- 포트: 8888
- 프로필: `local`, `dev`, `prod` (공통 요구사항 4번)
- 암호화와 Spring Cloud Bus를 통한 설정 일괄 갱신은 추후 구현합니다.

참고 코드: `ljw2869/msa`의 `config-service`, 설정 저장소 예시 `ljw2869/spring-cloud-config`

## 로컬 실행

Java 17과 GitHub에 연결 가능한 네트워크가 필요합니다.
Eureka 등록을 확인하려면 Discovery Server(8761)를 먼저 실행합니다.
저장소 루트에서 다음 명령을 실행합니다.

```powershell
cd infra/config-service
.\gradlew.bat bootRun
```

DB 없이 실행되며 Eureka Client로 `config-service`를 등록합니다.
Eureka 주소는 `EUREKA_SERVER_URL` 환경변수로 변경할 수 있으며,
기본값은 `http://localhost:8761/eureka/`입니다.
Eureka가 실행되지 않으면 등록 재시도 로그가 발생할 수 있습니다.
시작할 때 공개 설정 저장소
`https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-config`의 `main` 브랜치를
복제하므로 Git 연결에 실패하면 기동에 실패합니다.

## 확인

- 서버 상태: http://localhost:8888/actuator/health
- Eureka 등록: http://localhost:8761 (`CONFIG-SERVICE`가 `UP`인지 확인)
- Gateway의 local 설정: http://localhost:8888/gateway-service/local
- User의 local 설정: http://localhost:8888/user-service/local

설정 조회 API는 `/{application}/{profile}` 형식입니다.
응답의 `propertySources`에서 Git 설정 파일의 출처와 값을 확인할 수 있습니다.
`local` 파일이 없으면 존재하는 공통 설정과 서비스 공통 설정을 제공합니다.
Config Server는 Git 설정을 읽어 제공하며, 설정 수정은 `hyeja-config`에
커밋하고 push하여 수행합니다.

현재는 `bootstrap.yml`, 암호화 키, Bus/refresh를 구성하지 않습니다.
비밀번호나 API 키를 설정 저장소에 평문으로 커밋하지 않습니다.

빌드: 서비스 디렉터리에서 `.\gradlew.bat build`

종료: 실행한 터미널에서 `Ctrl+C`
