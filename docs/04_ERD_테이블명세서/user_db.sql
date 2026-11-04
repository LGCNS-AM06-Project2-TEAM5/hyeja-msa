-- user-service · user_db
-- 기준: hyeja-msa user-service JPA 엔티티 (Member, Profile, Region, MemberOutbox, BaseEntity)
-- MSA 변경: profile → region FK 제거(지역 코드 값만 저장), region은 서버 시작 시 CSV로 따로 채우는 읽기 전용 복사본입니다(policy_db와 실시간 동기화하지 않습니다), member-withdrawn 발행용 member_outbox 추가
-- COMMENT = 논리명 / 열 끝 주석 = 값·설명

CREATE TABLE `member` (
  `member_id`                BIGINT        NOT NULL  AUTO_INCREMENT COMMENT '회원 ID',  -- AUTO_INCREMENT. Gateway JWT의 sub와 X-Member-Id 헤더 값입니다. 다른 서비스는 이 값만 저장하고 FK는 두지 않습니다.
  `email`                    VARCHAR(100)  NOT NULL  COMMENT '이메일',  -- 로그인 ID, 이메일 인증을 마친 주소만 가입할 수 있습니다. profile의 키라서 가입 후 바꾸지 않습니다. 탈퇴한 행도 남아 있어 같은 이메일로 다시 가입할 수 없습니다. UNIQUE(uk_member_email)
  `password`                 VARCHAR(100)  NOT NULL  COMMENT '비밀번호',  -- BCrypt로 암호화한 값입니다. 가입 규칙은 영문·숫자·특수문자를 포함한 8~20자입니다(암호화 전 기준).
  `nickname`                 VARCHAR(20)   NOT NULL  COMMENT '닉네임',  -- 이메일 찾기에서 생년월일과 함께 씁니다. 탈퇴한 행도 중복 검사에 포함됩니다. UNIQUE(uk_member_nickname)
  `role`                     VARCHAR(10)   NOT NULL  COMMENT '권한',  -- USER(일반 회원), ADMIN(관리자). 가입하면 항상 USER입니다. JWT role claim으로 Gateway에 전달합니다.
  `created_at`               DATETIME(6)   NOT NULL  COMMENT '생성 시각',  -- JPA @CreatedDate 자동 기록, 수정 불가
  `updated_at`               DATETIME(6)   NOT NULL  COMMENT '수정 시각',  -- JPA @LastModifiedDate 자동 갱신
  `deleted_at`               DATETIME(6)   NULL      COMMENT '탈퇴 시각',  -- 회원 탈퇴 때 profile과 함께 채웁니다. 로그인·조회는 NULL인 행만 씁니다.
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_member_email` (`email`),
  UNIQUE KEY `uk_member_nickname` (`nickname`)
) COMMENT = '회원';

CREATE TABLE `profile` (
  `email`                    VARCHAR(100)  NOT NULL  COMMENT '이메일',  -- member.email. PK이자 FK라서 회원 1명당 조건 1개입니다. 가입할 때 회원과 같은 트랜잭션에서 저장합니다.
  `region_code`              CHAR(5)       NOT NULL  COMMENT '거주 시군구 코드',  -- region.region_code 값이지만 FK는 두지 않습니다. 가입·수정 때 user_db region에 없는 코드면 REGION_001 오류를 냅니다.
  `birth`                    DATE          NOT NULL  COMMENT '생년월일',  -- 오늘 이전 날짜만 받습니다. 정책 나이 조건 판정에 씁니다.
  `employment_code`          VARCHAR(20)   NOT NULL  COMMENT '취업 상태',  -- EMPLOYED(재직자), SELF_EMPLOYED(자영업자), UNEMPLOYED(미취업자), FREELANCER(프리랜서), DAILY_WORKER(일용근로자), ENTREPRENEUR((예비)창업자), SHORT_TERM_WORKER(단기근로자), FARMER(영농종사자), OTHER(기타)
  `houseless_yn`             BIT(1)        NOT NULL  COMMENT '무주택 여부',  -- 1이면 무주택입니다.
  `marriage_code`            VARCHAR(10)   NULL      COMMENT '혼인 상태',  -- SINGLE(미혼), MARRIED(기혼). 선택 항목입니다.
  `income_range_code`        VARCHAR(10)   NULL      COMMENT '연소득 구간',  -- 개인 연소득: UNDER_2000(2천만 원 미만), R2000_3000(2천만~3천만 원 미만), R3000_4000(3천만~4천만 원 미만), R4000_5000(4천만~5천만 원 미만), OVER_5000(5천만 원 이상). 선택 항목입니다.
  `education_code`           VARCHAR(30)   NULL      COMMENT '학력',  -- BELOW_HIGH_SCHOOL(고졸 미만), HIGH_SCHOOL_STUDENT(고교 재학), HIGH_SCHOOL_EXPECTED_GRADUATE(고졸 예정), HIGH_SCHOOL_GRADUATE(고교 졸업), COLLEGE_STUDENT(대학 재학), COLLEGE_EXPECTED_GRADUATE(대졸 예정), COLLEGE_GRADUATE(대학 졸업), MASTER_OR_DOCTOR(석박사), OTHER(기타). 선택 항목입니다.
  `housing_type`             VARCHAR(20)   NULL      COMMENT '주거 형태',  -- PARENTS(부모님 집), MONTHLY_RENT(월세), JEONSE(전세), OWNED(자가). 선택 항목입니다.
  `created_at`               DATETIME(6)   NOT NULL  COMMENT '생성 시각',  -- JPA @CreatedDate 자동 기록, 수정 불가
  `updated_at`               DATETIME(6)   NOT NULL  COMMENT '수정 시각',  -- JPA @LastModifiedDate 자동 갱신. 내 조건 수정은 전체 교체라서 선택 항목에 NULL이 오면 기존 값이 지워집니다.
  `deleted_at`               DATETIME(6)   NULL      COMMENT '삭제 시각',  -- 회원 탈퇴 때 member와 함께 채웁니다. 조회는 NULL인 행만 씁니다.
  PRIMARY KEY (`email`),
  CONSTRAINT `fk_profile_member_email` FOREIGN KEY (`email`) REFERENCES `member` (`email`)
) COMMENT = '회원 조건';

CREATE TABLE `region` (
  `region_code`              CHAR(5)       NOT NULL  COMMENT '시군구 코드',  -- 법정동코드 10자리 중 앞 5자리 (행정표준코드관리시스템), 시군구 256건
  `sigungu_name`             VARCHAR(30)   NOT NULL  COMMENT '시군구 이름',  -- '시·도명 시군구명' (예: 서울특별시 종로구), 세종은 '세종특별자치시'. 내부 프로필 API(F1·F6)의 sigunguName으로 내려갑니다.
  `created_at`               DATETIME(6)   NOT NULL  COMMENT '생성 시각',  -- JPA @CreatedDate 자동 기록, 수정 불가
  `updated_at`               DATETIME(6)   NOT NULL  COMMENT '수정 시각',  -- JPA @LastModifiedDate 자동 갱신
  `deleted_at`               DATETIME(6)   NULL      COMMENT '삭제 시각',  -- 소프트 삭제용. 현재 이 값을 채우는 코드는 없습니다.
  PRIMARY KEY (`region_code`)
) COMMENT = '시·군·구 (읽기 전용 복사본)';  -- 서버가 켜질 때 csv/region_sigungu.csv로 채웁니다. 있는 코드는 이름만 갱신하고 없는 코드는 추가합니다. policy를 호출하지 않습니다.

CREATE TABLE `member_outbox` (
  `outbox_id`                BIGINT        NOT NULL  AUTO_INCREMENT COMMENT 'Outbox ID',  -- AUTO_INCREMENT. Relay가 이 순서대로 발행합니다.
  `event_id`                 VARCHAR(36)   NOT NULL  COMMENT '이벤트 ID',  -- UUID. 구독 쪽(engagement)이 중복 처리를 확인할 때 씁니다. UNIQUE (엔티티에 키 이름이 없어 DB가 이름을 정합니다)
  `aggregate_id`             VARCHAR(20)   NOT NULL  COMMENT '회원 ID',  -- member_id의 문자열 값입니다. FK는 두지 않습니다. Kafka message key로 씁니다.
  `event_type`               VARCHAR(50)   NOT NULL  COMMENT '이벤트 종류',  -- member-withdrawn (Kafka 토픽 이름과 같습니다)
  `payload`                  TEXT          NOT NULL  COMMENT '본문',  -- eventId, eventType, eventVersion, occurredAt, aggregateId, memberId (JSON 문자열)
  `created_at`               DATETIME(6)   NOT NULL  COMMENT '생성 시각',  -- 회원 탈퇴와 같은 트랜잭션에서 기록합니다. 탈퇴가 롤백되면 이 행도 남지 않습니다.
  `published_at`             DATETIME(6)   NULL      COMMENT '발행 시각',  -- Relay(MemberOutboxRelay)가 Kafka 전송에 성공하면 채웁니다. NULL이면 아직 보내지 않은 이벤트이며 다음 스케줄에서 다시 보냅니다.
  PRIMARY KEY (`outbox_id`),
  UNIQUE KEY (`event_id`),
  KEY `idx_member_outbox_unpublished` (`published_at`, `outbox_id`)
) COMMENT = '회원 이벤트 발행 대기';
