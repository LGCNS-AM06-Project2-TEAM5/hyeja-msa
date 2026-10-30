# 04 ERD · 테이블 명세서

| 서비스 | DB | 테이블 | 서비스 안에서 유지하는 FK |
|---|---|---|---|
| user | `user_db` | member, profile | profile → member |
| policy | `policy_db` | policy, policy_region, region, term, card_news | policy_region → policy·region, card_news → policy |
| engagement | `engagement_db` | favorite, notification, (정책 스냅샷) | 없음 (회원·정책은 ID 값만 저장) |
| recommendation | 없음 | - | - |

1차 ERD 대비 변경점
- 서비스 사이 FK 제거: profile → region, favorite → member·policy, notification → member·policy
- engagement에 정책 스냅샷 읽기 모델 추가 (관심 목록 응답·키워드 검색·D-7 알림용)
- 개발 환경은 MariaDB 하나에 논리 DB 3개, DB 계정은 서비스별로 분리

1차 ERD·테이블 명세서 원본: [hyeja-backend/docs](https://github.com/LGCNS-AM06-Project2-TEAM5/hyeja-backend/tree/develop/docs)
