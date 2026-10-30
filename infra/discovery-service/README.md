# discovery-service

담당: 이지원

Eureka 서버입니다. 모든 서비스가 여기에 등록되고, Gateway와 Feign은 서비스 이름으로 서로를 찾습니다.

- 포트: 8761
- 확인: `http://localhost:8761` 대시보드에 서비스 목록과 상태가 보이면 완료입니다. (공통 요구사항 2번)
- 참고 코드: `ljw2869/msa`의 `service-discovery`
