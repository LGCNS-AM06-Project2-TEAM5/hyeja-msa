# 04 ERD · 테이블 명세서

서비스별 DB(hyeja_member, hyeja_policy, hyeja_favorite, hyeja_notification)의 ERD와 테이블 명세를 둡니다.

1차 ERD 대비 변경점
- favorite, notification의 member·policy FK 제거 (ID만 저장)
- favorite에 정책명·마감일 스냅샷 컬럼 추가
- policy에 `analysis_status`, `raw_hash` 추가
