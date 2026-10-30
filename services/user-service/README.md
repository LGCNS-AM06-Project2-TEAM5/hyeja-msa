# user-service

담당: 강태현, 차현준 · 포트 8081 · DB `user_db` (member, profile)

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
| 제공 → recommendation, policy | Feign | 회원의 내 조건 조회 (생년월일, 거주지 코드, 취업 상태, 무주택, 혼인, 소득 구간, 학력, 주거 형태) |
| 사용 → policy | Feign 또는 지역 복사본 | 거주지 코드 검증과 시군구 이름 (지금 코드는 ProfileService·MemberService가 RegionRepository를 직접 사용) — **결정 필요** |
| 발행 | Kafka `member-registered` | 가입 완료 후 (웰컴 메일, 신규 기능 · 승인 후) |
| 발행 | Kafka `member-withdrawn` (제안) | 탈퇴 후 engagement의 관심 정책·알림 정리 (지금 코드는 한 트랜잭션에서 직접 삭제) — **결정 필요** |

## 바뀌는 점

- profile → region FK를 지우고 `region_code` 값만 저장합니다.
- 탈퇴 시 favorite·notification을 직접 지우지 않습니다.
