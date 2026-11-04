# 02 요구사항 명세서

기능·비기능 요구사항과 우선순위를 표기합니다. 명세서 본문은 [요구사항 명세서.docx](요구사항%20명세서.docx)입니다.

## 과제 요구사항별 구현 위치

명세서와 결과보고서에 근거를 적을 때 쓰는 표입니다. 상태는 develop 기준입니다.

| 구분 | 요구사항 | 상태 | 구현 | 근거 |
|---|---|---|---|---|
| 필수 1 | 서비스 분리 | 완료 | user·policy·engagement·recommendation 4개 | [설계서](../architecture/HYEJA_MSA_Architecture_Design.md) |
| 필수 2 | Eureka | 완료 | discovery-service, 서비스는 이름으로 서로 찾음 | [discovery-service](../../infra/discovery-service/README.md) |
| 필수 3 | API Gateway | 완료 | 단일 진입점 8000, JWT 검증, 회원 헤더 전달, CORS | [gateway-service](../../infra/gateway-service/README.md) |
| 필수 4 | Config 중앙화 | 완료 | Config Server(HTTPS, 서비스별 계정) + hyeja-config 저장소 | [config-service](../../infra/config-service/README.md) |
| 필수 5 | OpenFeign 동기 통신 | 완료 | F1~F6 | [서비스 간 계약](../api/README.md) |
| 필수 6 | Kafka 비동기 통신 | 완료 | `policy-updated`, `member-withdrawn` | [서비스 간 계약 3장](../api/README.md#3-kafka-이벤트) |
| 필수 7 | 도메인 API | 완료 | 외부 API를 서비스별로 나눠 배치 | [서비스 간 계약 1장](../api/README.md#1-외부-api-기존-계약-기준-알림-수동-생성-제외) |
| 필수 8 | 서비스별 DB 분리 | 완료 | user_db·policy_db·engagement_db, 서비스 사이 FK 제거 | [04 ERD](../04_ERD_테이블명세서/README.md) |
| 필수 9 | Docker Compose 통합 기동 | 완료 | `compose.yaml` 한 번으로 전체 실행 | [docker](../../docker/README.md) |
| 필수 10 | 핵심 차별화 기술 | 완료 | Kafka 이벤트 동기화(Outbox·중복 제거·재시도·DLT) | [테스트 #119](../05_테스트케이스/policy-final-integration-119.md) |
| 선택 | Cloud Bus | 완료 | Kafka `hyeja-config-bus`로 설정 일괄 갱신, `compose.bus-test.yaml`로 확인 | [PR #72](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-msa/pull/72) |
| 선택 | 설정 암호화 | 완료 | `{cipher}` 값, 암호화 키는 Config Server에만 | [config-service](../../infra/config-service/README.md) |
| 선택 | Circuit Breaker | 완료 | Resilience4j, F1~F6 모두 대체 응답 | 서비스별 README |
| 선택 | 분산 추적 | 완료 | Zipkin, 요청 하나가 Gateway → 서비스로 이어지는 trace | [테스트 #127](../05_테스트케이스/user-live-verification-127.md) |
| 선택 | 관리자 대시보드 | 완료 | 프런트 관리자 운영 화면(서비스 상태·요청·응답 시간·오류율·서킷 브레이커·이벤트), Grafana | [hyeja-frontend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-frontend) |
| 선택 | 알림 연동 | 완료 | 탈퇴 이벤트 DLT 발생 시 관리자 메일, GitHub 이벤트 디스코드 알림 | `DltNotificationService`, `discord-notify.yml` |
| 선택 | 클라우드 실배포 | 완료 | AWS EC2 한 대에 Docker Compose | [루트 README 배포](../../README.md#배포) |
| 선택 | 추가 핵심 기술 | 완료 | 모니터링(Prometheus·Grafana), CI/CD(GitHub Actions → GHCR) | [docker](../../docker/README.md) |
| 선택 | K8s | 하지 않음 | - | - |
| 선택 | 부하 테스트 | 하지 않음 | 시연 흐름을 반복 호출해 메모리만 확인(k6 같은 도구는 쓰지 않음) | - |
| 선택 | Saga | 하지 않음 | 탈퇴는 이벤트 + 재시도·DLT로 처리 | [서비스 간 계약 3장](../api/README.md#3-kafka-이벤트) |
