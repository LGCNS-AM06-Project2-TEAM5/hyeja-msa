# member-service

회원, 프로필(내 조건), 지역 기준 데이터를 맡습니다.

| 항목 | 내용 |
|---|---|
| 경로 | `/api/members/**`, `/api/regions` |
| 테이블 | member, profile, region (DB: hyeja_member) |
| 옮겨 올 코드 | [hyeja-backend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend)의 `domain/member`, `domain/profile`, `domain/region`, `global/security`(JWT 발급) |
| 외부 | Redis(인증 코드·로그아웃 토큰), Gmail SMTP |

- 제공(Feign으로 다른 서비스가 호출): 회원의 내 조건 조회
- 발행(Kafka): `member.withdrawn`, `mail.requested`
- 바뀌는 점: 탈퇴 시 관심 정책·알림을 직접 지우지 않고 `member.withdrawn` 이벤트를 발행합니다. 인증 메일은 notification-service가 보냅니다.
