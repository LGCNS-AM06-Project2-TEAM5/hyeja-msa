# 아키텍처

| 문서 | 내용 |
|---|---|
| [HYEJA_MSA_Architecture_Design.md](HYEJA_MSA_Architecture_Design.md) | 팀 MSA 설계서 (서비스 4개, DB 3개, Feign·Kafka, 위험과 결정 항목) |
| [설계_코드_대조.md](설계_코드_대조.md) | 설계서와 실제 코드 대조 결과, 빠진 호출, 팀 결정(#1) |
| [gateway-engagement-auth.md](gateway-engagement-auth.md) | Gateway → Engagement 내부 JWT(`X-Gateway-Assertion`) 계약 |
| [system-architecture.png](system-architecture.png) | **실제 구현** 시스템 아키텍처 (Gateway, 서비스 4개, Feign F1~F6·서킷 브레이커, 논리 DB 3개, Redis, Kafka·DLT, Eureka·Config, 관리자 메일, 모니터링). 루트 README에 씀 |
| [infra-architecture.png](infra-architecture.png) | **실제 구현** 인프라·배포 아키텍처 (로컬 프런트, EC2·보안 그룹, Docker Compose, GHCR 자동 발행과 Docker Hub `v1.0` 실제 배포, 내부 TLS). 루트 README에 씀 |
| [system-diagram.webp](system-diagram.webp) | **설계 단계** 시스템 구성도 (Gateway, 서비스, Kafka 토픽, DB, 외부 API, 메일) |
| [service-diagram.webp](service-diagram.webp) | **설계 단계** 서비스 구성도 (서비스 간 Feign·Kafka 흐름과 서비스별 DB) |

배포는 EC2 한 대에 Docker Compose로 확정했고 K8s는 하지 않습니다.

## 실제 구현 구성도

develop 코드와 실제 시연 배포 기준입니다.

![HYEJA MSA 시스템 아키텍처](system-architecture.png)

![HYEJA MSA 인프라 및 배포 아키텍처](infra-architecture.png)

## 설계 단계 구성도

처음 설계 때 그린 그림이라 실제 구현과 다릅니다.

- 시스템 구성도의 `member-registered`(웰컴 메일)·`notification-created`(마감 알림 메일)와 Notification Worker는 설계에만 있고 구현하지 않았습니다. 토픽만 만들어 둡니다. 반대로 구현한 `member-withdrawn`은 시스템 구성도에 없습니다.
- 두 그림 모두 DLT와 관리자 메일, 서킷 브레이커, Cloud Bus, Prometheus·Grafana·Zipkin 모니터링, CI/CD·EC2 배포 구성이 없습니다.

![설계 단계 시스템 구성도](system-diagram.webp)

![설계 단계 서비스 구성도](service-diagram.webp)
