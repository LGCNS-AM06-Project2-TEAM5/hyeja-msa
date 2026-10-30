# config-service

Spring Cloud Config Server입니다. 설정 파일은 별도 저장소 `hyeja-config`에서 읽습니다.

- 포트: 8888
- 프로필: `local`, `dev`, `prod` (공통 요구사항 4번)
- 비밀값은 `{cipher}`로 암호화해 저장합니다. (선택 요구사항 2번)
- 설정 일괄 갱신은 Spring Cloud Bus로 합니다. (선택 요구사항 1번)

참고 코드: `ljw2869/msa`의 `config-service`, 설정 저장소 예시 `ljw2869/spring-cloud-config`
