# 06 결과보고서 (발표 자료)

PPT가 기본입니다. HTML로 만들면 PDF로 저장하고 PPT로도 변환해 둘 다 제출합니다.

필수 내용과 가져다 쓸 자료입니다.

| 필수 내용 | 자료 |
|---|---|
| 프로젝트 필요성 | [00 전환 준비](../00_MSA_전환_준비.md) 1장, 루트 [README](../../README.md) |
| 팀 구성·역할 | [01 수행계획서](../01_수행계획서/README.md) 역할 표 |
| 구현 기능 | [02 요구사항별 구현 위치](../02_요구사항명세서/README.md), 서비스별 README |
| 이슈와 해결 | [설계·코드 대조](../architecture/설계_코드_대조.md), [05 테스트 기록](../05_테스트케이스/README.md), GitHub 이슈 |
| 회고 | 각자 작성 |
| 아키텍처 3종 | 서비스 구성도·시스템 구성도는 [architecture](../architecture/README.md), 배포 구성도는 아래 설명으로 그림을 만듭니다 |
| ERD | [04 ERD·테이블 명세서](../04_ERD_테이블명세서/README.md) |
| API 명세 | [서비스 간 계약](../api/README.md), [engagement API](../api/engagement-api.md) |
| 시연 흐름 | [07 기타](../07_기타/README.md)의 시연 순서 |

배포 구성도에 들어갈 내용: 사용자 브라우저(프런트 5173) → EC2 한 대(서울 리전, t3.large) 안의 Docker Compose → Gateway 8000 → Eureka·Config와 서비스 4개 → MariaDB(논리 DB 3개)·Kafka·Redis, 옆에 Prometheus·Grafana·Zipkin. 이미지는 GitHub Actions가 빌드해 레지스트리에 올리고 서버는 받기만 합니다. 저장소의 `compose.yaml`은 모든 포트를 `127.0.0.1`에만 열고, EC2에서는 덮어쓰기 파일로 Gateway 8000, Grafana 3000, Prometheus 9090만 밖으로 엽니다. 보안 그룹은 이 3개와 SSH 22만 허용한 IP에 엽니다.
