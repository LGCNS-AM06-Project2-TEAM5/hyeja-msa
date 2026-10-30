# 혜자 MSA 전환 준비 문서

LG CNS AM INSPIRE CAMP 6기 · 미니프로젝트 2 · 5조 · 작성일 2026-10-30 · 강사님 미팅 답변 반영

확인 기준: 혜자 백엔드·프런트엔드 코드 · 지원님 실습 저장소 `ljw2869/msa` `af02b0e` · 수강생 안내 PDF와 강의 녹취

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
| CardNewsService | ProfileService, FavoriteRepository 직접 사용 | 내 조건은 member, 관심 여부는 notification에 Feign 호출 |
| NotificationGenerationService | MemberService, FavoriteRepository 직접 사용 | 관심 정책과 같은 서비스(notification)에 두어 통신 없이 조회. 회원 확인은 이벤트로 받은 탈퇴 정보로 대체 |
| MemberService.withdraw | FavoriteRepository, NotificationRepository 직접 삭제 | 탈퇴 이벤트를 받아 notification이 정리. 실패 시 보상(Saga) |
| Region | profile FK, policy_region FK, 수집 시 지역 코드 확인 | 바뀌지 않는 기준 데이터. 처리 방식은 강사님 추가 답변 후 확정 (1-7) |

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

### 1-6. 버전 (10/30 강사님 답변으로 확정)

| 항목 | 혜자 | 수업 실습(ljw2869/msa) |
|---|---|---|
| Spring Boot | 4.1.1 | 3.5.x |
| Spring Cloud | 없음 | 2025.0.x (config-service만 2025.1.3) |
| 빌드 | Gradle | Maven |
| Java | 17 | 21 |

- **결정: 혜자 버전(Spring Boot 4.1.1)을 유지합니다.** 강사님 답변은 "버전을 굳이 내릴 필요 없다. 혜자에 맞춰 나머지 버전을 올려서 작업하라"였습니다.
- 그래서 새로 만드는 Eureka·Gateway·Config도 Boot 4.1.x + Spring Cloud **2025.1.2 이상**(공식 호환표 기준)으로 맞춥니다.
- 수업 코드(`ljw2869/msa`)는 구조 참고용으로만 쓰고, 의존성 이름과 설정은 새 버전에 맞게 확인합니다. Day 2 오전에 Eureka와 서비스 1개를 먼저 띄워 확인합니다.

### 1-7. 서비스 분리 (팀 확정 · 지원님 MSA 설계서 기준)

| 서비스 | 담당 | 테이블(DB) | 맡는 일 |
|---|---|---|---|
| user-service | 강태현, 차현준 | member, profile (`user_db`) | 가입·이메일 인증·로그인·JWT, 이메일 찾기, 탈퇴, 내 조건 8개 |
| policy-service | 김예준, 홍준서 | policy, policy_region, region, term, card_news (`policy_db`) | 정책 목록·상세·수집, 지역, 용어, AI 정책 분석·카드뉴스 |
| engagement-service | 손수연 | favorite, notification, 정책 스냅샷 (`engagement_db`) | 관심 정책, D-7 알림, (승인 시) 웰컴·마감 메일 |
| recommendation-service | 류지범 | 없음 | 맞춤 검색(`/api/policies/search`), 자격 판정, AI 의도 분석·판정 이유 |
| Eureka·Config·Gateway·Docker·K8s | 이지원 | - | 인프라 |

- AI는 별도 서비스로 두지 않습니다. 정책 분석·카드뉴스는 policy, 검색·판정 설명은 recommendation에 둡니다(설계서 원칙).
- 동기(Feign): recommendation→user·policy·engagement, policy→engagement, engagement→policy. **코드 대조로 추가 필요한 호출**: policy→user(내 조건), user→policy(지역), 정책 상세 판정 위치.
- 비동기(Kafka): `policy-updated`(우선), `member-registered`·`notification-created`(신규 메일, 승인 후), `member-withdrawn`(제안).
- 상세 계약과 대조 결과는 hyeja-msa 저장소의 `docs/api/README.md`, `docs/architecture/설계_코드_대조.md`에 있습니다.

**핵심 기술 후보**: Kafka 이벤트 동기화(`policy-updated` 멱등성·재시도·DLT, 1차 때 10분 걸리던 수집을 AI 분석과 분리) 또는 Saga(회원 탈퇴 보상).

### 1-8. 프런트 연동

- 강사님 답변: 기능을 추가하지 않으면 **호출 주소만 Gateway로 바꾸면 됩니다.** 기능을 추가하면 화면도 함께 고칩니다.
- 프런트 경로는 백엔드와 같아서 Gateway가 같은 경로로 라우팅하면 됩니다.
- 로컬 `.env`에 `VITE_API_BASE_URL=http://localhost:8000`, `VITE_USE_MOCK=false`를 넣습니다. 예시 파일의 기본값이 `true`라서 그대로 두면 가짜 데이터가 나옵니다.
- CORS 허용(`localhost:5173`)은 Gateway로 옮깁니다.

### 1-9. 혜자 문서와 코드가 다른 곳

| 항목 | 문서 | 코드 |
|---|---|---|
| 수집 최대 페이지 | `docs/architecture.md`: 20페이지 | `PolicyService`: 50페이지 |
| 토큰 유효 시간 | README: 30분 | `JwtProvider`: 300분 |

---

## 2. 궁금한 점과 강사님 답변 (10/30 미팅)

강사님이 **3, 9, 11, 13번**은 정리해서 다음 미팅 때 다시 답하고, 조별 노션에 올려 주기로 했습니다.

| 번호 | 질문 | 강사님 답변 | 우리 판단 |
|---|---|---|---|
| 1 | 서비스 분리 단위 | 정답 없음. 결합도가 크면 억지로 쪼개지 말고 효율로 판단. 3~4개 이상이면 됨. 발표 때 "이런 기준으로 나눴다"를 말하면 차별화됨 | 팀 확정: user, policy, engagement, recommendation 4개 (1-7) |
| 2 | 동기·비동기 기준 | 결과를 받아야 다음 작업을 할 수 있으면 동기(예: 계좌 인출). 결과가 당장 필요 없는 후속 작업은 비동기(예: 가입 환영 메일, 활동 포인트) | Feign 5개(+코드 대조로 2~3개), Kafka 토픽 3개(+탈퇴 제안) (1-7) |
| 3 | FK·JOIN 처리 | 둘 중 고르면 **내 서비스 DB에 복사**. 자주 조회하는데 매번 통신하는 건 비효율. 정리 자료는 다음 미팅 때 보여 주심 | favorite에 정책명·마감일 스냅샷 (**추가 답변 대기**) |
| 4 | 여러 서비스 작업 실패 시 일관성 | 이전 작업은 롤백이 안 되니 **보상 트랜잭션(Saga)**으로 취소해야 함. 핵심 차별화 기술 중 하나 | 회원 탈퇴에 Saga 적용 (핵심 기술 또는 추가 핵심 기술) |
| 5 | AI 별도 서비스 | **분리하는 게 좋음.** 다른 조도 AI 서비스를 따로 분리함 | 팀은 AI를 기능별로 나눠 policy·recommendation에 두기로 함. 검색·판정을 맡는 recommendation이 사실상 AI 중심 서비스 |
| 6 | 버전 | **내릴 필요 없음.** 혜자 버전에 맞춰 나머지를 올려서 작업 | Boot 4.1.1 유지, Spring Cloud 2025.1.2+ (1-6) |
| 7 | 인증 위치 | 수업 방식대로 **Gateway(진입점)에서 확인** | Gateway에서 JWT·블랙리스트·관리자 권한 확인 |
| 8 | 공통 코드·기준 데이터 | 서비스마다 복사는 비효율. **한곳에 모아 두는 게 낫다** | 공통 모듈(`common`). 지역 데이터 방식은 다음 미팅에서 확인 |
| 9 | 스케줄러 | 알림 서비스 하나를 만들면 됨. ERD를 보고 다시 답하기로 함 | 관심 정책과 알림을 engagement-service에 함께 둠 (**추가 답변 대기**) |
| 10 | Kafka 증빙 | 로그, 테스트 코드, 시연 영상 **모두** 담으면 가장 좋음. 일부러 실패하는 메시지를 만들어 로그로 확인 | 실패 케이스를 만드는 테스트와 시연 시나리오를 미리 설계 |
| 11 | 서비스 연동 테스트 | 아직 고민해 보지 않음. 다시 답하기로 함 | (**답변 대기**) 우선 서비스별 테스트 + Compose 통합 시연 |
| 12 | 프런트 | 기능 추가가 없으면 **호출 주소만 Gateway로** 바꾸면 됨 | `.env`만 변경 (1-8) |
| 13 | 저장소 구조 | 강제 사항 없음. 다른 조와 맞추도록 기준을 정해 내일 전체 공지 | 지금은 hyeja-msa 하나(모노레포). **공지 후 맞춤** |
| 14 | 배포 범위 | 클라우드·K8s는 **선택**. 필수 먼저 하고 여력이 있으면 진행. AWS 계정은 필요하면 제공 | 필수 1~10 우선, 여유 있으면 K8s → AWS |

**미팅 중 추가로 나온 이야기**

- **수집이 느린 문제**: 1차 때 정책 약 3,000건을 AI 분석까지 하며 받아오는 데 10분 가까이 걸렸습니다. "정책은 먼저 받고 AI 분석은 비동기로 나중에 입히면 어떤가"라는 질문에 강사님은 "좋은 고민"이라며 함께 고민해 보겠다고 했습니다. → 1-7의 `policy.collected` / `policy.analyzed` 흐름이 바로 이 방식입니다.
- **CI 도구**: 실무에서는 Jenkins와 GitHub Actions를 가장 많이 씁니다. 간단하게 적용하려면 **GitHub Actions**, 세밀하게 커스텀하려면 Jenkins입니다. → 우리는 GitHub Actions를 씁니다.
- **배포 방식**은 팀이 상황을 보고 정하면 되고, 개발하면서 바뀌어도 됩니다.

---

## 3. 진행 단계

**주의**: 제출이 11/05 오전 9시라 실제 작업 마감은 **11/04 밤**입니다.

| 단계 | 시점 | 할 일 | 끝났다고 보는 기준 |
|---|---|---|---|
| 0. 결정 | 10/30 | 팀장·역할, 서비스 분리(4개), 동기·비동기 구간, 핵심 기술 확정 | 수행계획서 초안 |
| 1. 설계 | 10/30~주말 | 서비스 구성도, 시스템 구성도, 서비스별 ERD, 내부 API·Kafka 이벤트 형식 정의, 요구사항 명세서·WBS. 강사님 추가 답변(3·9·11·13번) 반영 | 팀원 모두가 같은 문서를 보고 개발 가능 |
| 2. 인프라 골격 | 11/02 오전 | Boot 4.1 + Spring Cloud 2025.1.2+로 Eureka·Config·Gateway 기동, 공통 모듈. `ljw2869/msa`는 구조 참고 | Eureka 화면에 서비스 등록됨 |
| 3. 서비스 분리 | 11/02 | 담당자별로 hyeja-backend 코드를 복사해 나누기, DB 3개 분리, FK 제거, Gateway JWT 검증·관리자 권한·CORS 이전 | 각 서비스가 혼자 뜨고, Gateway를 거쳐 API가 응답함 |
| 4. 통신 연결 | 11/03 | Feign 연결(계약 문서 기준), Kafka `policy-updated`, 프런트 `.env` 변경 후 연결 | 가입 → 조건 등록 → 목록 → 관심 등록 → 알림 흐름이 끝까지 동작 |
| 5. 핵심 기술 | 11/03~04 | 수집 → AI 비동기 처리, 멱등성, 재시도, DLT. 실패 케이스 테스트와 로그 | 중복·장애 상황을 로그·테스트·영상으로 증빙 |
| 6. 컨테이너 통합 | 11/04 | 서비스별 Dockerfile, Docker Compose로 전체 기동 | `docker compose up` 한 번으로 전체 동작 |
| 7. 선택 요구사항 | 11/04 | Circuit Breaker, 설정 암호화, Bus, Zipkin, 알림 메일, Saga(탈퇴). 여유가 있으면 K8s → AWS | 항목별 캡처 |
| 8. 마무리 | 11/04 밤 | 테스트 케이스, 시연 영상 5분, 결과보고서, 발표 자료 | 11/05 오전 9시 전 제출 |
