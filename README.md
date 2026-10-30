# 혜자 MSA (hyeja-msa)

청년 주거 정책 추천 서비스 「혜자」를 마이크로서비스 아키텍처로 전환하는 LG CNS AM INSPIRE CAMP 6기 미니프로젝트 2 · 5조 저장소입니다.

- 1차 혜자 원본 사본(참고용, 수정 금지): [hyeja-backend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend) · [hyeja-frontend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-frontend) — 원본 [Hyejabonjuui](https://github.com/Hyejabonjuui) 저장소를 그대로 가져와 보관(archive)했습니다.
- 설정 저장소(Config Server용): [hyeja-config](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-config)
- 기간: 2026-10-30(금) ~ 2026-11-05(목), 산출물 제출 11-05 오전 9시

## 저장소 구조

```text
hyeja-msa/
├── infra/
│   ├── discovery-service/  Eureka 서버
│   ├── gateway-service/    Spring Cloud Gateway (단일 진입점, JWT 검증, CORS, 라우팅)
│   └── config-service/     Spring Cloud Config Server (hyeja-config 저장소를 읽음)
├── services/
│   ├── member-service/       회원·프로필·지역, 로그인·JWT 발급, 탈퇴
│   ├── policy-service/       정책 수집·조회·맞춤 검색, 카드뉴스, 용어
│   ├── ai-service/           OpenAI 호출 전담 (DB 없음)
│   ├── favorite-service/     관심 정책
│   └── notification-service/ 마감 알림, 메일 발송
├── docker/                 Docker Compose 통합 실행
├── k8s/                    (선택) Kubernetes 매니페스트
└── docs/                   산출물, 아키텍처, API·이벤트 계약, 회의록
```

서비스 분리는 **초안**입니다. 강사님 미팅 결과에 따라 바뀔 수 있으며, 확정되면 이 문서와 `docs/`를 함께 고칩니다.

## 서비스와 포트 (초안)

| 서비스 | 포트 | DB | 담당 |
|---|---|---|---|
| discovery-service | 8761 | - | |
| gateway-service | 8000 | - | |
| config-service | 8888 | - | |
| member-service | 8081 | hyeja_member | |
| policy-service | 8082 | hyeja_policy | |
| ai-service | 8083 | - | |
| favorite-service | 8084 | hyeja_favorite | |
| notification-service | 8085 | hyeja_notification | |

## 협업 규칙

자세한 내용은 [docs/협업규칙.md](docs/협업규칙.md)에 있습니다.

- `main`: 발표·제출용. `develop`에서만 PR로 합칩니다.
- `develop`: 기본 브랜치. 모든 작업 브랜치는 여기서 만들고 여기로 PR을 보냅니다.
- 작업 브랜치: `<tag>/<이슈번호>/<영문-설명>` (예: `feat/12/member-service-split`)
- 커밋: `<tag> : <변경 내용>` (예: `feat : 회원 서비스 Eureka 등록`)
- 라벨: 작업 종류(`feat` 등) 1개 + 영역(`member` 등) 1개 이상
- 비밀값(`.env`, API 키, 비밀번호)은 커밋하지 않습니다.

## 실행 방법

서비스 골격이 만들어지면 채웁니다. 목표는 `docker compose up` 한 번으로 전체가 뜨는 것입니다.

서비스 코드는 [hyeja-backend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend)에서 필요한 부분을 복사해 `services/` 아래로 옮깁니다. hyeja-backend와 hyeja-frontend(1차 원본 사본)에는 커밋하지 않습니다.

프런트는 [hyeja-frontend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-frontend)를 받아 로컬 `.env`에 `VITE_API_BASE_URL=http://localhost:8000`, `VITE_USE_MOCK=false`를 넣고 실행합니다. (`.env`는 커밋하지 않으므로 원본 사본은 그대로 유지됩니다.)

## 팀 (5조)

| 이름 | GitHub | 역할 |
|---|---|---|
| 차현준 | | |
| 강태현 | | |
| 김예준 | [@yejun05011](https://github.com/yejun05011) | |
| 류지범 | [@JibeomRyu](https://github.com/JibeomRyu) | |
| 손수연 | | |
| 이지원 | [@ljw2869](https://github.com/ljw2869) | 1차 혜자 원작 |
| 홍준서 | [@jnnxeo](https://github.com/jnnxeo) | |
