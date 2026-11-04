# 05 테스트 케이스

주요 기능별 입력, 기대 결과, P/F를 기록합니다. 서비스 간 연동(Feign, Kafka)과 장애 상황 테스트를 포함합니다.

- [Recommendation–Policy F2 실제 연동 검증 — Issue #77](f2-live-verification-77.md): 실제 F1·F2·F3 및 판정·후보 제한·장애·복구 확인(AI만 대체).
- [Policy 실제 환경 검증 — Issue #73](policy-live-verification-73.md): 실제 API·AI·MariaDB 소량 수집 및 재수집, 조회·F2/F5 검증 결과.

- [Engagement–Policy F5 실제 연동 검증 — Issue #112](f5-live-verification-112.md): 실제 Eureka·Policy·Engagement·MariaDB로 관심 등록·오류·장애·복구 확인.
- [User 실제 환경 기능 검증 — Issue #127](user-live-verification-127.md): Compose 전체 실행으로 가입·로그인·내 조건·보안·탈퇴 이벤트 확인, F1·F6는 내부 호출과 빈 응답만 확인(정책 0건, 결과 내용 미검증).
