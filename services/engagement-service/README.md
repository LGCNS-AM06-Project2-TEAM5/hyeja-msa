# engagement-service

담당: 손수연 · 포트 8083 · DB `engagement_db` (favorite, notification, 정책 스냅샷 읽기 모델)

관심 정책(♡) 등록·해제·목록(키워드 검색·페이징), 관심 여부 일괄 조회, 마감 D-7 앱 알림 생성·조회·읽음·삭제, (여유 있을 때) 마감 알림 메일 발송을 맡습니다.

## 외부 API (Gateway 경로, 기존 계약 유지)

| 메서드 | 경로 | 인증 |
|---|---|---|
| GET | `/api/favorite` | 로그인 |
| POST | `/api/favorite/{policyId}` | 로그인 |
| DELETE | `/api/favorite/{policyId}` | 로그인 |
| GET | `/api/notification` | 로그인 |
| PATCH | `/api/notification/{notificationId}/read` | 로그인 |
| DELETE | `/api/notification/{notificationId}` | 로그인 |
| POST | `/api/notification/admin/generate` | 관리자 |

## 옮겨 올 코드 (hyeja-backend)

- [`domain/favorite`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/favorite), [`domain/notification`](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/src/main/java/com/hyeja/domain/notification) (스케줄러 매일 00:05 Asia/Seoul)

## 다른 서비스와의 연결

| 방향 | 방식 | 내용 |
|---|---|---|
| 제공 → policy, recommendation | Feign | 회원의 관심 정책 ID 일괄 조회 |
| 사용 → policy | Feign | 관심 등록 시 정책 존재·스냅샷 확인 |
| 구독 | Kafka `policy-updated` | 정책 스냅샷 읽기 모델 갱신 |
| 발행·구독 | Kafka `notification-created` | 내부 Worker가 마감 알림 메일 발송 (여유 있을 때, #1 확정) |
| 구독 | Kafka `member-withdrawn` | 탈퇴 회원의 관심 정책·알림 정리. `eventId`로 중복 무시, 실패 시 재시도 후 DLT (#1 확정) |

## 정책 스냅샷 읽기 모델

관심 목록 응답과 키워드 검색(정책명·지원 내용 LIKE)이 지금은 policy를 JOIN합니다. 같은 결과를 내려면 아래 값을 engagement에 복사해 둡니다.

`policyId, policyName, categories, supportContent, applyEndDate, applyPeriodCode, applyUrl, activeYn, deleted`

## 바뀌는 점

- favorite·notification의 member·policy FK를 지우고 ID만 저장합니다.
- 알림 대상 계산은 회원 탈퇴 여부와 정책 노출·마감 상태를 이 서비스 안의 데이터로 판단합니다.
