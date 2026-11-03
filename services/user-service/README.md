# user-service

담당: 강태현, 차현준 · 포트 8081 · DB `user_db` (member, profile, region 읽기 복사본)

회원가입, 이메일 인증, 로그인·로그아웃(JWT 발급), 내 계정·이메일 찾기, 회원 탈퇴, 내 조건(프로필 8개 항목) 저장·조회를 맡습니다.

## 외부 API (Gateway 경로, 기존 계약 유지)

| 메서드 | 경로 | 인증 |
|---|---|---|
| POST | `/api/members` | 없음 (회원가입 + 내 조건 함께 저장) |
| POST | `/api/members/email-verifications` | 없음 |
| POST | `/api/members/email-verifications/confirmation` | 없음 |
| POST | `/api/members/login` | 없음 |
| POST | `/api/members/logout` | 로그인 |
| GET | `/api/members/me` | 로그인 |
| GET | `/api/members/find-email` | 없음 |
| PATCH | `/api/members/me/delete` | 로그인 |
| GET | `/api/members/me/profile` | 로그인 |
| PATCH | `/api/members/me/profile` | 로그인 |

## 옮겨 올 코드 (hyeja-backend)

- [`domain/member`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/member), [`domain/profile`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/profile)
- [`global/security`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/global/security) 중 JWT 발급(JwtProvider), 로그아웃 블랙리스트(TokenBlacklist, Redis)
- 이메일 인증(EmailVerificationService, Redis, Gmail SMTP)

## 다른 서비스와의 연결

| 방향 | 방식 | 내용 |
|---|---|---|
| 제공 → recommendation, policy | Feign `GET /internal/members/{memberId}/profile` | 회원의 내 조건 조회 (생년월일, 거주지 코드·시군구 이름, 취업 상태, 무주택, 혼인, 소득 구간, 학력, 주거 형태). 응답·오류는 [서비스 간 계약](../../docs/api/README.md#f1f6-회원-조건-조회-user-제공) |
| 지역 복사본 | user_db `region` | 거주지 코드 검증과 시군구 이름. 원본은 policy와 같은 `region_sigungu.csv`이고, user_db `region`은 기동 때 이 파일로 적재하는 읽기 전용 복사본입니다. CSV가 바뀌면 두 서비스가 다시 기동해 맞춥니다. policy는 호출하지 않습니다 (#1 확정) |
| 발행 | Kafka `member-registered` | 웰컴 메일용. 이번에 하지 않음 (#1 확정) |
| 발행 | Kafka `member-withdrawn` | 탈퇴 트랜잭션에 `member_outbox` 행을 함께 기록합니다. 후속 Relay가 Kafka로 보내면 engagement가 관심 정책·알림을 정리합니다. Saga는 선택 (#1 확정) |

## 바뀌는 점

- profile → region FK를 지우고 `region_code` 값만 저장합니다.
- 탈퇴 시 favorite·notification을 직접 지우지 않습니다.
- 탈퇴 이벤트는 Member·Profile 변경과 같은 트랜잭션에 Outbox로 기록해 누락을 방지합니다.
- Relay는 미발행 Outbox를 순서대로 `member-withdrawn` 토픽에 전송하고 성공한 행에 `published_at`을 기록합니다.
