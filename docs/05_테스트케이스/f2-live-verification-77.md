# Recommendation–Policy F2 실제 서비스 연동 검증 — Issue #77

- 검증일: 2026-11-04 (Asia/Seoul)
- 작업 브랜치: `test/77/f2-live-verification`
- 기준 커밋: `583587017afe06176386f24a8abb0a3e2e95dca8` — PR #92 머지 후 develop
- 관련 구현: F1 #56, F2 제공 #57, F1·F2·F3 호출 #66, F3 제공 #84
- 결과: 최종 실행의 **45개 확인 항목 모두 통과**. 운영 코드·기본 설정 변경 없음.

## 1. 실제 호출과 대체 범위

```text
합성 X-Member-Id를 포함한 검색 HTTP 요청
 → 실제 Recommendation /api/policies/search
 → 실제 User F1 (회원 조건)
 → 검증용 의도 분석 대체 / 해시태그 처리
 → Eureka + OpenFeign으로 실제 Policy F2
 → 실제 Engagement F3 (관심 여부)
 → 기존 자격 판정·기본 이유 생성·3그룹 분류
```

F1·F2·F3는 모두 별도 JVM의 실제 서비스이며 모의 HTTP 서버로 대체하지 않았다.
정책·회원 조건·관심 데이터는 서로 다른 MariaDB 스키마에 준비했다.
OpenAI만 검증용 `@Primary PolicySearchAiAnalyzer`로 대체했다.
자연어 의도는 고정 카테고리를 반환하고, 이유 분석은 예외를 발생시켜 기존 기본 이유 생성 경로를 사용했다.
따라서 실제 OpenAI 분석·자연어 이해 품질·AI가 판정을 하향 조정하는 동작을 검증한 것은 아니다.

[검증 전용 실행 보조 코드](support/f2/F2LiveFixture.java)는 운영 소스 경로 밖에 있다.
원래 운영 JAR은 변경하지 않고 별도 fixture.jar를 Spring Boot PropertiesLauncher로 추가했다.
Recommendation의 실제 Client·Adapter·검색 서비스·판정기를 사용하고, Policy에는 F2 요청 본문을 기록하는 검증용 Filter만 추가했다.
해당 기록에는 합성 지역·카테고리·날짜만 포함되며 실제 회원·키·토큰은 사용하지 않았다.

## 2. 환경·빌드

| 항목 | 값 |
|---|---|
| 실행 | Java 17, 서비스별 `-Xms64m -Xmx384m` |
| Eureka | 127.0.0.1:54688 |
| Policy / Engagement | 54689 / 54690 |
| User / Recommendation | 54691 / 54692 |
| DB | MariaDB 13.0.2, TCP 3307 |
| 검증 스키마 | f2_77_policy_08d514 / f2_77_engagement_08d514 / f2_77_user_08d514 |
| DB 권한 | 서비스별 계정 3개, 각자 자기 검증 스키마에만 권한 |
| 검색 활성화 | 실행 환경에서만 `RECOMMENDATION_SEARCH_ENABLED=true` |
| F2 Timeout | 기존 연결 2초·응답 5초 유지 |
| 제외 | Config Server·Cloud Bus·Kafka·실제 Redis/메일·실제 OpenAI |

Docker 미실행으로 로컬 JVM을 사용했다. User는 내부 프로필 조회만 수행하므로 Redis·메일 상태 확인을 비활성화했고 로그인·인증 기능을 사용하지 않았다.
실제 Gateway를 통과하지 않고 검색 컨트롤러에 합성 `X-Member-Id`를 주었다. 사용자 인증 검증 완료를 뜻하지 않는다.
기존 서비스 DB와 #73·#112 검증 DB는 변경하지 않았다.

| 모듈 | 자동 테스트 | 실패·오류·건너뜀 | bootJar |
|---|---:|---:|---|
| Recommendation | 7 | 0 | 성공, 이번 작업에서 실행 |
| User | 100 | 0 | 성공, 이번 작업에서 실행 |
| Policy | 247 | 0 | 동일 기준 커밋의 #112 빌드·JAR 재사용 |
| Engagement | 57 | 0 | 동일 기준 커밋의 #112 빌드·JAR 재사용 |
| Eureka | 테스트 없음 | 0 | 동일 기준 커밋의 #112 JAR 재사용 |

로컬 미추적 복사본이 빌드에 섞이지 않도록 Git에 커밋된 소스를 추출했다.
서비스 JAR의 BOOT-INF/classes·lib를 컴파일 경로로 사용해 F2LiveFixture.java를 javac로 컴파일하고 fixture.jar로 묶었다.

## 3. 요청·응답 계약 확인

실제 Recommendation에서 Policy로 전달된 F2 요청 예:

```json
{"categories":["MONTHLY_RENT"],"regionCode":"11440","today":"2026-11-04"}
```

합성 회원 7702의 실제 User 프로필에는 `26350`을 저장했고, 해당 회원으로 검색하면 F2 요청의 regionCode도 `26350`으로 변경됐다.
검증용 의도 분석기가 JEONSE를 반환한 자연어 검색에서는 F2 요청도 JEONSE로 전달됐다.
카테고리·지역·today를 직접 HTTP 전송 직후의 기록으로 확인했으며 회원 ID·검색 문장은 F2 요청에 포함되지 않았다.

| 확인 대상 | 실제 결과 |
|---|---|
| 전체 대상 지역 | 마포구 매칭 정책의 응답에 마포구·강남구 모두 반환 |
| null·enum | incomeMin/Max·추가 자격 null 및 취업·무주택·기간 enum 유지 |
| 후보 선정 전 자격 제외 안 함 | 나이 부적격 정책도 후보에 포함되고 Recommendation에서 declined로 분류 |
| 전국 정책 | regions 빈 배열로 반환되고 거주지와 무관하게 포함 |
| 지역별 후보 | 회원 지역 변경 시 일치 후보 집합도 변경 |
| 시·도 구형 코드 | 11000 정책은 11440 회원의 후보에 포함 |
| 카테고리 빈 배열 | 전체 카테고리 조회 |
| 카테고리 복수 | 요청한 카테고리 중 하나라도 맞으면 포함 |
| 노출·날짜 필터 | 삭제·비활성·CLOSED·신청 시작 전·마감 경과 정책 제외 |
| 시작·마감 당일 | 포함 |
| 정렬 | 마감일 오름차순, null 마지막, 같은 날짜는 ID 오름차순 |
| 최대 20건 | 합성 22건 중 정렬된 20건만 선정 |
| 제한과 판정 순서 | 후보 20건 모두 나이 부적격이어도 먼저 20건을 받고 declined 20건으로 응답 |

## 4. 검색·장애·복구 결과

| 시나리오 | 실제 결과 |
|---|---|
| 조건 충족·미확인·불충족 정책 3개 검색 | approved/underReview/declined 각 1건, 정책 ID 일치 |
| 실제 Engagement에 관심 등록 후 검색 | 해당 정책 isFavorite=true |
| Engagement 일시 정지 | 결과 3건 유지, 관심 여부만 모두 false로 대체 |
| 정상 빈 후보 | 200/SUCCESS_001, 세 그룹과 count 모두 빈 값/0 |
| Policy 일시 정지 | 약 5.015초 후 500/SERVER_001, 정상 빈 결과로 처리하지 않음 |
| Policy 프로세스 종료 | 약 0.023초 후 500/SERVER_001, 정상 빈 결과로 처리하지 않음 |
| Policy 재시작 | Eureka 전파 후 정상 200, 기존 후보 20건 재조회 |
| 재시작 직후 전파 | Health UP 뒤 약 1.207초 후 검색 성공 확인 |

지연 시간은 합성 데이터의 단일 요청 관측값이다. 성능·동시 부하 테스트로 해석하지 않는다.
최종 실행의 세부 HTTP 응답·요청·DB 조건 확인을 합쳐 45개 단언을 통과했다.
검증 프로세스 5개는 모두 종료했고 포트가 닫힌 것을 확인했다.

## 5. 별도 관측: 구형 시·도 코드 지역 판정

F2 계약대로 11000(서울 전체) 정책은 11440(마포구) 회원의 후보에 포함됐다.
그러나 Recommendation의 기존 `PolicyEligibilityEvaluator.evaluateRegion`은 지역 코드를 정확히 비교하므로 이 정책의 지역 상태는 DISABLE이고 declined 그룹으로 분류됐다.

이는 이번 F2 연결에서 새로 도입한 로직이 아니라 기존 판정기의 관측 결과다.
새 수집 흐름은 시·도 코드를 시군구로 확장하지만, 구형 데이터가 남는 경우의 자격 판정 의미는 Recommendation 담당자와 별도 확인할 사항이다.
이번 검증에서 타 담당 판정 코드를 수정하지 않았다. 시·도 정책의 최종 자격 판정까지 올바르게 처리했다고 표현하지 않는다.

## 6. 재현 설정과 절차

1. 기준 커밋에서 각 서비스의 테스트·bootJar를 실행한다.
2. MariaDB 검증 스키마 3개와 각 스키마 전용 계정을 준비한다.
3. F2LiveFixture.java를 Policy·Recommendation JAR의 BOOT-INF/classes·lib로 컴파일하고 fixture.jar로 묶는다.
4. Eureka·Engagement·User는 운영 JAR로, Policy·Recommendation은 아래 검증 진입점으로 실행한다.
5. 모든 서비스가 Eureka에 UP으로 등록된 후 합성 회원·지역·정책·관심 데이터를 준비한다.
6. 위 표 순서대로 검색과 직접 F2 호출을 수행하고, 실제 F2 요청 본문 및 최종 검색 JSON을 대조한다.
7. 자신이 시작한 검증 프로세스만 일시 정지·재개·종료·재시작하며 장애와 복구를 확인한다.
8. 실행기가 만든 서버를 종료한다. 검증 데이터와 비밀값 파일은 Git 밖에 보관한다.

검증 진입점의 JVM 실행 형태:

```bash
java -Xms64m -Xmx384m \
  -Dloader.path=<fixture.jar의 절대 경로> \
  '-Dloader.main=verification.f2.F2LiveFixture$Recommendation' \
  -cp <recommendation 운영 JAR> \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --eureka.client.service-url.defaultZone=http://127.0.0.1:<EUREKA_PORT>/eureka/
```

Policy는 loader.main을 `verification.f2.F2LiveFixture$Policy`로 지정하고 `-Df2.wire.file=<합성 F2 요청 기록 파일>`을 추가한다.

공통 설정:

- 검증용 프로필, 로컬 포트·127.0.0.1 바인딩, Config Client·Cloud Bus 비활성화
- 각 DB 서비스의 별도 datasource URL·계정, `ddl-auto=update`, `open-in-view=false`
- Eureka 등록·목록 갱신·서버 응답 캐시 주기 1초, LoadBalancer 캐시 비활성화
- User JWT·Engagement assertion은 합성 테스트 전용 키를 사용
- Redis·SMTP health 비활성화, 외부 AI·온통청년은 기동용 가짜 값만 주입
- 실제 서비스명 기반 통신을 위해 Feign 직접 URL은 설정하지 않음
- Recommendation 검색 활성화는 런타임 환경에서만 적용

정책 fixture는 기본 나이19~34·취업제한없음·무주택제한없음·소득제한없음으로 구성하고, 소득UNKNOWN 정책과 나이40~60 정책을 추가해 세 그룹을 재현했다.
실제 개인정보 대신 합성 회원7701·7702, 생년월일1998-01-01, 거주지11440·26350을 사용했다.
22건 제한 fixture는 같은 마감일의 ID LIMIT-00~20 및 null 마감일 정책1개이며, 기대 후보는 LIMIT-00~19이다.

초기 합성 데이터 SQL의 BIT 값 처리 오류와, Health UP 직후 검색을 바로 호출한 복구 검사 실패는 실행기에서 수정했다.
최종 결과는 DB를 다시 준비하고 전체 시나리오를 재실행한 성공 기록이다. 운영 코드 변경으로 문제를 가린 것이 아니다.

## 7. 미검증 범위

- 실제 OpenAI 의도·이유 분석 및 AI 최종 판정 보정
- 실제 Gateway JWT 로그인·프런트 화면·Docker Compose/CD 배포
- Redis·메일 기능, F6, Kafka·Outbox 이벤트 종단간, 서킷 브레이커
- 전체 정책 수집량·동시 부하·한국 날짜 자정 전후 경계

#112는 독립된 F5 문서 작업이며 이 검증의 선행 PR이 아니다.
