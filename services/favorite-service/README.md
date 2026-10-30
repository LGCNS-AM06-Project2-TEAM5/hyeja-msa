# favorite-service

관심 정책(♡)을 맡습니다.

| 항목 | 내용 |
|---|---|
| 경로 | `/api/favorite/**` |
| 테이블 | favorite (DB: hyeja_favorite) |
| 옮겨 올 코드 | [hyeja-backend](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend)의 `domain/favorite` |

- 호출(Feign): policy-service(등록 시 정책 존재·마감일 확인)
- 제공(Feign): 회원의 관심 정책 ID 목록, 마감 D-7 대상 목록
- 구독(Kafka): `policy.updated`(정책명·마감일 갱신), `member.withdrawn`(삭제)
- 바뀌는 점: member·policy FK를 없애고 ID와 정책명·마감일 복사본(스냅샷)을 저장합니다.
