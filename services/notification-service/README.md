# notification-service

관심 정책 마감 알림과 메일 발송을 맡습니다.

| 항목 | 내용 |
|---|---|
| 경로 | `/api/notification/**` |
| 테이블 | notification (DB: hyeja_notification) |
| 옮겨 올 코드 | [hyeja-backend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend)의 `domain/notification`, 메일 발송 부분(`EmailVerificationService`의 발송) |
| 외부 | Gmail SMTP |

- 스케줄러: 매일 00:05(Asia/Seoul) 마감 D-7 알림 생성
- 호출(Feign): favorite-service(D-7 대상)
- 구독(Kafka): `mail.requested`(인증 메일 발송), `member.withdrawn`(삭제)
- 바뀌는 점: member·policy FK를 없애고 ID와 마감일만 저장합니다.
