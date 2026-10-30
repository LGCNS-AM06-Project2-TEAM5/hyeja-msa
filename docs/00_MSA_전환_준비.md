# 혜자 MSA 전환 준비 문서

LG CNS AM INSPIRE CAMP 6기 · 미니프로젝트 2 · 5조 · 작성일 2026-10-30

확인 기준: `Hyejabonjuui/backend` main `3e47bb1` · `Hyejabonjuui/frontend` main `ec635f9` · 지원님 실습 저장소 `ljw2869/msa` `af02b0e` · 수강생 안내 PDF와 강의 녹취

---

## 1. 알아야 할 내용

### 1-1. 미니프로젝트 2에서 해야 하는 것

기능을 새로 만드는 프로젝트가 아닙니다. 완성된 혜자를 **나누고, 연결하고, 컨테이너로 배포하는 작업**입니다. 기능 추가는 필요할 때만 합니다.

| 구분 | 내용 | 배점 |
|---|---|---|
| 필수 1~9 | 서비스 분리(3~4개 이상), Eureka, Gateway, Config 중앙화, OpenFeign 동기 통신, Kafka 비동기 통신, 도메인 API, 서비스별 DB 분리, Dockerfile과 Docker Compose 통합 기동 | 각 7점 |
| 필수 10 | 핵심 차별화 기술 1개 (CI/CD, Kafka 이벤트 동기화, Saga, Chaos Engineering, 모니터링, AI 중 선택) | 7점 |
| 선택 | Bus, 설정 암호화, K8s, 클라우드 실배포, Circuit Breaker, 분산 추적, 부하 테스트, 관리자 대시보드, 알림 연동, 추가 핵심 기술 | 각 3점 |
| 정성 평가 | 산출물 완성도 ±10, 아키텍처 적합성 ±5, 발표·시연 ±5 | ±20점 |

| 제출물 | 내용 |
|---|---|
| 산출물 7종 | 수행계획서, 요구사항 명세서, WBS, ERD·테이블 명세서, 테스트 케이스, 결과보고서(발표 자료), 기타 자료(저장소 주소, 화면설계서, 시연 영상) |
| 아키텍처 3종 | 서비스 구성도, 시스템 구성도(동기/비동기 구분, DB 포함), 배포 구성도 |
| 시연 영상 | 5분, **이번엔 필수** |
| 발표 자료 | PPT 기본. HTML로 만들면 PDF와 PPT 둘 다 제출 |
| 협업 기록 | 노션 등록, 매일 오전 할 일 공유, 저녁 7시 완료 공유, 회의록 |

- **제출**: 11/05(목) 오전 9시. 발표는 같은 날 오후 1시부터 조별 15분 + Q&A 5분입니다.
- **컨테이너**: 필수입니다. 모든 서비스와 Eureka·Gateway·Config를 Docker Compose로 한 번에 띄워야 합니다.
- **클라우드 네이티브**: MSA와 컨테이너는 필수, K8s와 클라우드 실배포는 선택입니다. 강사님은 클라우드 배포까지 해 보기를 권했습니다.
- **수업에서 안 다룬 것**: Kafka 재시도·DLT·멱등성, Saga, k6 부하 테스트. Docker Compose는 9월 DevOps 특강에서 설치까지만 했습니다.

### 1-2. 혜자 현재 상태 (GitHub 확인 결과)

혜자는 지원님이 1차에서 진행한 프로젝트(7조 혜자본주의)입니다.

| 항목 | backend | frontend |
|---|---|---|
| 브랜치 | main 하나 | main 하나 |
| 열린 PR·이슈 | 없음 (마지막 PR #148, 9/29) | 없음 (마지막 PR #88, 9/29) |
| CI | PR마다 `test` + `e2eTest` 실행, 마지막 실행 성공 | 마지막 실행 성공 |
| 규모 | Java 124개 파일, 약 7,800줄, 테스트 63개 | 소스 154개 파일, 테스트 63개 |
| 기술 | Java 17, **Spring Boot 4.1.1**, Gradle, JPA, MariaDB, Redis, Security·JWT, Mail, OpenAI SDK, Swagger | React 19, Vite 8, MUI 9, axios, Vitest·MSW·Playwright |

**도메인과 API 경로** (경로가 도메인별로 나뉘어 있어 Gateway 라우팅에 그대로 쓸 수 있습니다)

| 도메인 | API 경로 | 테이블 |
|---|---|---|
| member | `/api/members`, `/login`, `/logout`, `/me`, `/me/delete`, `/find-email`, `/email-verifications/**` | member |
| profile | `/api/members/me/profile` | profile |
| region | `/api/regions` | region |
| policy | `/api/policies/housing`, `/housing/me`, `/{policyId}`, `/search`, `/sync`(관리자) | policy, policy_region |
| cardnews | `/api/policies/card-news`, `/card-news/guest`, `/card-detail/{policyId}` | card_news |
| term | `/api/terms`, `/api/terms/{termId}` | term |
| favorite | `/api/favorite`, `/api/favorite/{policyId}` | favorite |
| notification | `/api/notification/**`, `/api/notification/admin/generate`(관리자) | notification |

### 1-3. 핵심 흐름 (코드 기준)

1. **정책 수집** (`POST /api/policies/sync`, 관리자만)
   - 온통청년 API를 페이지당 100건씩 받아 `주거` 분류이면서 승인된 정책만 고릅니다.
   - 정책마다 OpenAI를 1번 호출해 요약, 주거 유형, 무주택·소득 조건을 분석합니다.
   - 분석 결과로 policy, policy_region, card_news 4장을 정책 단위 트랜잭션 하나로 저장합니다.
   - **AI 분석이 실패하면 그 정책은 저장되지 않습니다.**
   - 자동 스케줄은 없고 관리자가 수동으로 실행합니다. 저장소에 정책 시드는 없어서, 데이터는 이 API로만 생깁니다.
2. **맞춤 검색** (`GET /api/policies/search`)
   - OpenAI를 최대 2번(질문 의도 분류, 판정 이유 문장) 호출합니다. 해시태그 검색이면 의도 분류는 건너뜁니다.
   - 두 호출 모두 실패하면 기본 동작으로 대체하는 처리가 이미 있습니다. Circuit Breaker 대체 응답으로 그대로 쓸 수 있습니다.
3. **인증**
   - JWT에는 회원 ID와 권한(role)만 들어 있고, 유효 시간은 300분입니다.
   - 인증 필터는 DB를 조회하지 않고 토큰 서명과 Redis 블랙리스트만 확인합니다. 그래서 Gateway로 옮기기 쉬운 구조입니다.
   - 관리자 전용 경로는 `/api/policies/sync`, `/api/notification/admin/**` 두 곳입니다.
4. **인증 메일**: Redis에 60초 쿨다운을 걸고, Gmail SMTP로 **동기 발송**합니다. 실패하면 바로 MAIL_001 오류를 돌려줍니다.
5. **마감 알림**: 매일 00:05에 관심 정책 중 7일 뒤 마감인 것을 찾아 웹 알림을 만듭니다. 메일 발송은 없습니다.
6. **회원 탈퇴**: 알림 삭제 → 관심 정책 삭제 → 프로필·회원 소프트 삭제 → 토큰 블랙리스트를 **한 트랜잭션**으로 처리합니다.

### 1-4. 서비스를 나누면 끊어지는 연결

| 지금 코드 | 무엇이 얽혀 있나 | MSA에서 바꿀 방향 |
|---|---|---|
| Favorite 엔티티·쿼리 | member, policy FK. 목록·키워드 검색·알림 대상 조회에서 policy를 join fetch | FK 제거, ID만 저장. 정책명·마감일·노출 여부를 복사(스냅샷) |
| Notification 엔티티·쿼리 | member, policy FK와 join fetch | FK 제거, ID와 마감일만 저장 |
| CardNews 쿼리 | policy, policy_region과 join (노출·마감·지역 조건) | **policy와 같은 서비스에 둬야 함** |
| PolicyService, PolicySearchService | ProfileService(내 조건), FavoriteRepository(하트 표시), TermRepository 직접 사용 | 내 조건·하트는 Feign 호출, term은 같은 서비스 |
| CardNewsService | ProfileService, FavoriteRepository 직접 사용 | Feign 호출 |
| NotificationGenerationService | MemberService, FavoriteRepository 직접 사용 | 관심 정책 서비스 조회 또는 이벤트로 받은 복사본 사용 |
| MemberService.withdraw | FavoriteRepository, NotificationRepository 직접 삭제 | 탈퇴 이벤트를 받아 각 서비스가 정리 |
| Region | profile FK, policy_region FK, 수집 시 지역 코드 확인 | 바뀌지 않는 기준 데이터라 필요한 서비스마다 복사 |

### 1-5. Kafka와 MSA 구성요소는 어디에 있나

**혜자 저장소에는 Kafka, Eureka, Gateway, Config Server, 앱 Dockerfile이 없습니다.** 지금 있는 인프라는 다음과 같습니다.

- Redis용 `compose.yaml`
- GitHub Actions CI
- Testcontainers(MariaDB·Redis) E2E 테스트

MSA 구성요소는 **지원님 개인 실습 저장소**에 있습니다. 수업 코드를 따라 만든 것이라 골격 참고와 재사용에 쓸 수 있습니다.

| 저장소 | 들어 있는 것 |
|---|---|
| `ljw2869/msa` | apigateway-service(JWT 검증 필터, 로깅·글로벌 필터), service-discovery(Eureka), config-service(Bus·RabbitMQ), user·order·catalog 서비스, Kafka(order 발행 → catalog 구독), OpenFeign과 ErrorDecoder, Resilience4j, Zipkin, Prometheus |
| `ljw2869/spring-cloud-config` | Config Server가 읽는 설정 파일 예시(user-service, ecommerce dev·prod) |
| `ljw2869/kubernetes-public` | nginx Deployment, LoadBalancer 매니페스트 |

### 1-6. 버전 차이 (Day 2 시작 전에 정해야 함)

| 항목 | 혜자 | 수업 실습(ljw2869/msa) |
|---|---|---|
| Spring Boot | 4.1.1 | 3.5.x |
| Spring Cloud | 없음 | 2025.0.x (config-service만 2025.1.3) |
| 빌드 | Gradle | Maven |
| Java | 17 | 21 |

- **A안. 혜자 버전 유지**: Spring Cloud 공식 호환표상 Boot 4.1.x는 Spring Cloud **2025.1.2 이상**에서 지원됩니다. 수업 코드의 설정이 일부 다를 수 있습니다.
- **B안. 혜자를 Boot 3.5로 내림**: 수업 코드를 그대로 쓸 수 있습니다. 대신 Boot 4 전용 의존성 이름(`spring-boot-starter-webmvc`, 테스트 starter 등)을 바꾸고 테스트를 다시 통과시켜야 합니다.
- Day 2 오전에 Eureka와 서비스 1개를 띄워 보고 결정하는 것을 권합니다.

### 1-7. 서비스 분리 초안 (강사님 답변 후 확정)

| 서비스 | 경로 | 테이블 | 하는 일 |
|---|---|---|---|
| member-service | `/api/members/**`, `/api/regions` | member, profile, region | 가입, 로그인, JWT 발급, 내 조건, 탈퇴, Redis |
| policy-service | `/api/policies/**`, `/api/terms/**` | policy, policy_region, card_news, term, region | 수집, 목록·상세·맞춤 목록, 맞춤 검색, 카드뉴스 |
| ai-service | 내부 API만 | 없음 | OpenAI 정책 분석, 검색 의도 분류, 판정 이유 |
| favorite-service | `/api/favorite/**` | favorite | 관심 정책 |
| notification-service | `/api/notification/**` | notification | D-7 알림, 메일 발송 |

여기에 Eureka, Gateway, Config Server, Kafka, Redis, MariaDB를 더합니다.

**동기 통신 (OpenFeign)**

| 호출 | 이유 | 장애 시 대체 |
|---|---|---|
| policy → member | 맞춤 목록·검색에 내 조건 필요 | 비로그인 목록 |
| policy → favorite | 목록의 하트 표시 | 하트 없이 목록 표시 |
| policy → ai | 검색 의도 분류, 판정 이유 | 지금 코드의 기본 동작 |
| favorite → policy | 등록 시 정책 존재·마감일 확인 | 등록 실패 안내 |
| notification → favorite | 00:05 D-7 대상 조회 | 다음 실행 때 재시도 |

**비동기 통신 (Kafka)**

| 토픽 | 흐름 | 효과 |
|---|---|---|
| `policy.collected` | policy → ai | 수집은 원문 저장 후 바로 끝남. AI 실패가 수집을 막지 않음 |
| `policy.analyzed` | ai → policy | 분석 반영, 카드뉴스 생성, 노출 시작 |
| `policy.updated` | policy → favorite | 정책명·마감일 스냅샷 갱신 |
| `member.withdrawn` | member → favorite, notification | 탈퇴 데이터 정리 |
| `mail.requested` | member → notification | 인증 메일 비동기 발송 |

**ERD 변경**

- favorite, notification의 member·policy FK 제거
- favorite에 정책명·마감일 스냅샷 컬럼 추가
- policy에 `analysis_status`(분석 상태)와 `raw_hash`(원문 변경 확인) 추가

**핵심 기술 후보**: Kafka 이벤트 동기화. 재수집할 때 같은 정책이 다시 들어오므로 멱등성 처리가 실제로 필요하고, card_news·notification에 이미 중복 방지 UK가 있습니다.

### 1-8. 프런트 연동

- 프런트가 부르는 경로는 백엔드와 같습니다. Gateway가 같은 경로로 라우팅하면 프런트 코드는 고치지 않아도 됩니다.
- 바꿀 것은 `.env` 두 줄입니다. `VITE_API_BASE_URL`을 Gateway(`:8000`)로, `VITE_USE_MOCK`를 `false`로 바꿉니다. 예시 파일의 기본값이 `true`라서 그대로 두면 가짜 데이터가 나옵니다.
- CORS 허용(`localhost:5173`)이 지금은 백엔드 보안 설정에 있습니다. Gateway로 옮겨야 합니다.

### 1-9. 혜자 문서와 코드가 다른 곳

| 항목 | 문서 | 코드 |
|---|---|---|
| 수집 최대 페이지 | `docs/architecture.md`: 20페이지 | `PolicyService`: 50페이지 |
| 토큰 유효 시간 | README: 30분 | `JwtProvider`: 300분 |

---

## 2. 궁금한 점 (강사님께)

우선순위 순서입니다. 시간이 부족하면 1~6번을 먼저 여쭙습니다.

**1. 현재 프로젝트를 MSA로 전환할 때, 어느 정도 단위까지 서비스를 분리하는 것이 적절한지?**

→ 현재는 회원, 프로필, 정책, 관심 정책, 알림 등 여러 도메인으로 나뉘어 있습니다. 각 도메인을 모두 독립된 서비스로 분리하는 방법도 있고, 관련된 도메인끼리 묶어서 하나의 서비스로 구성하는 방법도 있을 것 같습니다.

**2. 모놀리스에서는 다른 도메인의 service나 repository를 바로 호출하는데, MSA로 나누면 동기 호출과 비동기 호출은 어떤 기준으로 나누어야 하는지?**

→ 예를 들어 REST/Feign 같은 동기 호출 방식과 Kafka 등 이벤트를 활용한 비동기 방식이 있을 때, 각각 어떤 상황에서 사용하는 것이 적절한지 궁금합니다.

**3. 서비스별로 DB를 나누면, 다른 서비스의 데이터를 참조하던 FK와 JOIN은 어떻게 처리하는 것이 적절한지?**

→ 지금은 관심 정책과 알림이 회원·정책 테이블을 FK로 참조하고, 목록 조회에서 정책을 함께 가져옵니다. 화면에 필요한 정책명·마감일을 매번 다른 서비스에 조회하는 방법과, 내 서비스 DB에 복사해 두는 방법 중 어느 쪽이 적절한지 궁금합니다.

**4. 여러 서비스에 걸친 작업이 중간에 실패하면 데이터 일관성은 어디까지 맞춰야 하는지?**

→ 지금 회원 탈퇴는 알림 삭제, 관심 정책 삭제, 회원 삭제를 한 트랜잭션으로 처리합니다. 서비스를 나누면 한 번에 롤백할 수 없습니다. 이벤트로 순서대로 정리하고 잠깐 어긋나는 것을 허용해도 되는지, Saga 보상 트랜잭션까지 구현해야 하는지 궁금합니다.

**5. AI처럼 느리고 실패할 수 있는 외부 API는 별도 서비스로 분리하는 것이 좋은지?**

→ 지금은 정책을 수집할 때 정책마다 OpenAI 분석을 거치고, 분석에 실패하면 그 정책이 저장되지 않습니다. AI 호출을 별도 서비스로 빼서 비동기로 처리하는 것이 좋은지, 정책 서비스 안에 두고 타임아웃과 Circuit Breaker만 거는 것이 좋은지 궁금합니다.

**6. 버전이 수업과 다를 때 어떻게 맞추는 것이 좋은지?**

→ 혜자는 Spring Boot 4.1.1(Gradle, Java 17)이고, 수업 실습은 Spring Boot 3.5와 Spring Cloud 2025.0(Maven, Java 21)입니다. 공식 호환표상 Boot 4.1은 Spring Cloud 2025.1.2 이상에서 지원됩니다. 혜자 버전을 유지하는 것과 수업 버전으로 내리는 것 중 어느 쪽을 권하시는지 궁금합니다.

**7. 로그인 인증과 권한 확인은 Gateway와 각 서비스 중 어디에서 하는 것이 적절한지?**

→ 지금은 서버 하나가 JWT 검증, 로그아웃 토큰 블랙리스트(Redis), 관리자 권한 확인을 모두 합니다. Gateway에서 한 번만 검증하고 각 서비스는 전달받은 회원 ID만 믿어도 되는지, 서비스마다 다시 검증해야 하는지 궁금합니다.

**8. 여러 서비스가 함께 쓰는 공통 코드와 기준 데이터는 어떻게 관리하는지?**

→ 공통 응답 형식, 예외 처리, enum(취업 상태, 소득 구간 등)과 시군구 지역 데이터를 여러 도메인이 같이 씁니다. 서비스마다 복사해 두는 방법과 공통 모듈이나 한 서비스에 두고 조회하는 방법 중 어느 쪽이 맞는지 궁금합니다.

**9. 스케줄러로 도는 작업은 MSA에서 어떻게 다뤄야 하는지?**

→ 지금은 매일 00:05에 관심 정책의 마감 D-7 알림을 만드는 스케줄러가 있습니다. 서비스를 나누면 알림 서비스가 관심 정책과 정책 마감일을 직접 조회할 수 없고, 같은 서비스를 여러 개 띄우면 스케줄러가 중복 실행될 수 있습니다. 이런 작업은 어느 서비스가 맡고 어떻게 설계하는 것이 적절한지 궁금합니다.

**10. 핵심 기술로 Kafka 이벤트 동기화를 고르면, 재시도와 멱등성은 어떻게 증빙하면 되는지?**

→ 로그, 테스트 코드, 시연 영상 중 어떤 방식이 평가에서 인정되는지 궁금합니다.

**11. 서비스 사이 연동은 어떻게 테스트하고 증빙하는 것이 좋은지?**

→ 지금은 서버 하나를 띄워 E2E 테스트를 합니다. 서비스가 나뉘면 서비스별 단위 테스트와 Docker Compose로 전체를 띄운 통합 시연 중 어느 수준을 기대하시는지 궁금합니다.

**12. 기존 프런트엔드는 그대로 써도 되는지?**

→ API 경로를 그대로 두고 Gateway가 라우팅하면 프런트는 서버 주소만 바꾸면 됩니다. 프런트 기능 추가 없이 이렇게 진행해도 되는지 궁금합니다.

**13. 저장소는 서비스마다 따로 만들어야 하는지?**

→ 산출물에 "서비스별 저장소 주소"가 있는데, 저장소 하나에 서비스별 폴더를 두는 방식(모노레포)도 인정되는지 궁금합니다.

**14. 배포 범위는 어디까지 기대하시는지?**

→ K8s 배포를 수업 때 만든 VMware 로컬 클러스터로 해도 되는지, 클라우드 실배포에 수업용 AWS 계정을 쓸 수 있는지 궁금합니다.

---

## 3. 진행 단계

**주의**: 제출이 11/05 오전 9시라 실제 작업 마감은 **11/04 밤**입니다.

| 단계 | 시점 | 할 일 | 끝났다고 보는 기준 |
|---|---|---|---|
| 0. 결정 | 10/30 | 팀장·역할, 핵심 기술, 서비스 분리 초안 확정. 강사님 미팅 | 수행계획서 초안 |
| 1. 설계 | 10/30~주말 | 서비스 구성도, 시스템 구성도, 서비스별 ERD, 내부 API·Kafka 이벤트 형식 정의, 요구사항 명세서·WBS | 팀원 모두가 같은 문서를 보고 개발 가능 |
| 2. 인프라 골격 | 11/02 오전 | 버전 결정(A/B안), Eureka·Config·Gateway 기동. `ljw2869/msa` 골격 참고 | Eureka 화면에 서비스 등록됨 |
| 3. 서비스 분리 | 11/02 | 기존 코드를 서비스별로 복사한 뒤 잘라내기, DB 분리, FK 제거, Gateway JWT 검증·관리자 권한·CORS 이전 | 각 서비스가 혼자 뜨고, Gateway를 거쳐 API가 응답함 |
| 4. 통신 연결 | 11/03 | Feign 5개, Kafka 토픽 5개, 프런트 `.env` 변경 후 연결 | 가입 → 조건 등록 → 목록 → 관심 등록 → 알림 흐름이 끝까지 동작 |
| 5. 핵심 기술 | 11/03~04 | 멱등성, 재시도, DLT, 수집 → AI → 카드뉴스 비동기 처리 | 중복·장애 상황 시연 가능 |
| 6. 컨테이너 통합 | 11/04 | 서비스별 Dockerfile, Docker Compose로 전체 기동 | `docker compose up` 한 번으로 전체 동작 |
| 7. 선택 요구사항 | 11/04 | Circuit Breaker, 설정 암호화, Bus, Zipkin, 알림 메일. 여유가 있으면 K8s | 항목별 캡처 |
| 8. 마무리 | 11/04 밤 | 테스트 케이스, 시연 영상 5분, 결과보고서, 발표 자료 | 11/05 오전 9시 전 제출 |
