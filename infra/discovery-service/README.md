# discovery-service

담당: 이지원

Eureka 서버입니다. 모든 서비스가 여기에 등록되고, Gateway와 Feign은 서비스 이름으로 서로를 찾습니다.

- 포트: 8761
- 확인: `http://localhost:8761` 대시보드에 서비스 목록과 상태가 보이면 완료입니다. (공통 요구사항 2번)
- 참고 코드: `ljw2869/msa`의 `service-discovery`

## 로컬 실행

Java 17이 필요합니다. 저장소 루트에서 다음 명령을 실행합니다.

```powershell
cd infra/discovery-service
.\gradlew.bat bootRun
```

- 대시보드: http://localhost:8761
- 상태 확인: http://localhost:8761/actuator/health (`UP`)
- 종료: 실행한 터미널에서 `Ctrl+C`

Config Server와 DB 없이 독립 실행됩니다. 다른 서비스가 등록되기 전에는
대시보드의 서비스 목록이 비어 있는 것이 정상입니다.

빌드만 확인하려면 서비스 디렉터리에서 `.\gradlew.bat build`를 실행합니다.
