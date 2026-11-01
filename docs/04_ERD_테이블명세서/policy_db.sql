-- policy-service · policy_db
-- 기준: hyeja-backend JPA 엔티티 (Policy, PolicyRegion, Region, Term, CardNews, BaseEntity)
-- MSA 변경: 다른 서비스 DB를 가리키는 FK 없음(서비스 안 FK만 유지), policy-updated 발행용 policy_outbox 추가(팀 확정 필요)
-- COMMENT = 논리명 / 열 끝 주석 = 값·설명

CREATE TABLE `region` (
  `region_code`              CHAR(5)       NOT NULL  COMMENT '시군구 코드',  -- 법정동코드 10자리 중 앞 5자리 (행정표준코드관리시스템), 시군구 256건
  `sigungu_name`             VARCHAR(30)   NOT NULL  COMMENT '시군구 이름',  -- '시·도명 시군구명' (예: 서울특별시 종로구), 세종은 '세종특별자치시'
  `created_at`               DATETIME      NOT NULL  COMMENT '생성 시각',  -- JPA @CreatedDate 자동 기록, 수정 불가
  `updated_at`               DATETIME      NOT NULL  COMMENT '수정 시각',  -- JPA @LastModifiedDate 자동 갱신
  `deleted_at`               DATETIME      NULL      COMMENT '삭제 시각',  -- 소프트 삭제용. 조회는 NULL인 행만 씀. 현재 이 값을 채우는 코드는 없음
  PRIMARY KEY (`region_code`)
) COMMENT = '시·군·구';

CREATE TABLE `policy` (
  `policy_id`                VARCHAR(30)   NOT NULL  COMMENT '정책 ID',  -- 온통청년 API plcyNo 그대로 저장, 수정 불가
  `policy_name`              VARCHAR(200)  NOT NULL  COMMENT '정책명',  -- API plcyNm, 비어 있으면 '제목 없음'
  `category`                 VARCHAR(100)  NOT NULL  COMMENT '주거 하위 유형',  -- AI 판정, 쉼표로 여러 개: MONTHLY_RENT(월세), JEONSE(전세), PURCHASE(청약·구입), PUBLIC_RENT(공공임대), OTHER(기타 주거). 판정 결과가 없으면 OTHER, OTHER가 다른 유형과 함께 나오면 OTHER를 뺌
  `api_sub_category`         VARCHAR(50)   NULL      COMMENT 'API 중분류',  -- API mclsfNm (온통청년 중분류 원문)
  `keywords`                 VARCHAR(500)  NULL      COMMENT '키워드',  -- API plcyKywdNm 원문
  `description`              TEXT          NULL      COMMENT '정책 요약',  -- AI 요약 1~2문장 (정책명·지원 내용·참여 대상 근거)
  `support_content`          TEXT          NULL      COMMENT '지원 내용',  -- API plcySprtCn 원문
  `min_age`                  INT           NULL      COMMENT '최소 나이',  -- API sprtTrgtMinAge, 0 이하이거나 숫자가 아니면 NULL
  `max_age`                  INT           NULL      COMMENT '최대 나이',  -- API sprtTrgtMaxAge, 0 이하이거나 숫자가 아니면 NULL
  `age_limit_yn`             BOOLEAN       NOT NULL  COMMENT '나이 제한 여부',  -- API sprtTrgtAgeLmtYn=Y이고 최소·최대 나이 중 하나라도 있을 때만 true
  `income_condition_code`    VARCHAR(20)   NULL      COMMENT '소득 조건 유형',  -- AI 판정: NO_RESTRICTION(제한 없음), COMPARABLE(개인 연소득으로 비교 가능), CONDITIONAL(중위소득·가구 합산 등), UNKNOWN(정보 없음). COMPARABLE인데 금액이 없으면 UNKNOWN으로 바꿈
  `income_min`               INT           NULL      COMMENT '연소득 하한',  -- COMPARABLE일 때만 개인 연소득 하한, 원 단위. 하한이 없으면 NULL
  `income_max`               INT           NULL      COMMENT '연소득 상한',  -- COMPARABLE일 때만 개인 연소득 상한, 원 단위. 상한이 없으면 NULL
  `income_etc`               TEXT          NULL      COMMENT '소득 기타 조건',  -- API earnEtcCn 원문
  `marriage_code`            VARCHAR(20)   NULL      COMMENT '혼인 조건',  -- API mrgSttsCd 변환: 55001 MARRIED(기혼), 55002 SINGLE(미혼), 55003 NO_RESTRICTION(제한없음)
  `employment_codes`         VARCHAR(200)  NULL      COMMENT '취업 조건',  -- API jobCd 13001~13010 변환 10종(재직자·자영업자·미취업자·프리랜서·일용근로자·(예비)창업자·단기근로자·영농종사자·기타·제한없음), 쉼표로 여러 개
  `houseless_yn`             VARCHAR(20)   NOT NULL  COMMENT '무주택 조건',  -- AI 판정: REQUIRED(무주택 필수), NOT_REQUIRED(무관하다고 명시), UNKNOWN(정보 없음)
  `housing_type`             VARCHAR(20)   NULL      COMMENT '주거 유형(원문)',  -- API mclsfNm 원문 (api_sub_category와 같은 값, 읽는 코드 없음). 길이 20으로 api_sub_category(50)보다 짧음
  `apply_period_code`        VARCHAR(20)   NOT NULL  COMMENT '신청 기간 구분',  -- API aplyPrdSeCd 변환: 57001 SPECIFIC_PERIOD(특정기간), 57002 ALWAYS(상시), 57003 CLOSED(마감). 코드가 없거나 모르는 값이면 그 정책은 저장 안 함
  `extra_qualification`      TEXT          NULL      COMMENT '추가 자격',  -- API addAplyQlfcCndCn + ptcpPrpTrgtCn(참여 대상)을 줄바꿈으로 연결, 두 값이 같으면 하나만
  `apply_start_date`         DATE          NULL      COMMENT '신청 시작일',  -- SPECIFIC_PERIOD일 때만 API aplyYmd의 첫 날짜
  `apply_end_date`           DATE          NULL      COMMENT '신청 마감일',  -- SPECIFIC_PERIOD일 때만 API aplyYmd의 마지막 날짜. 마감순 정렬 기준(NULL은 뒤로)
  `apply_method`             TEXT          NULL      COMMENT '신청 방법',  -- API plcyAplyMthdCn 원문
  `apply_url`                VARCHAR(500)  NULL      COMMENT '신청 링크',  -- API aplyUrlAddr
  `ref_url`                  VARCHAR(500)  NULL      COMMENT '참고 링크',  -- API refUrlAddr1, 없으면 refUrlAddr2
  `view_count`               INT           NOT NULL  COMMENT '조회수',  -- API inqCnt(온통청년 조회수), 없으면 0. 조회순 정렬 기준. DB 기본값 없음(저장 시 앱이 0으로 채움)
  `active_yn`                BOOLEAN       NOT NULL  COMMENT '노출 여부',  -- API 승인 상태 44002(승인)이면 true. 수집 단계에서 승인 정책만 저장하므로 현재는 항상 true. false면 정책 목록·검색·카드뉴스·관심 목록에서 제외. DB 기본값 없음(저장 시 앱이 true로 채움)
  `created_at`               DATETIME      NOT NULL  COMMENT '생성 시각',  -- JPA @CreatedDate 자동 기록, 수정 불가
  `updated_at`               DATETIME      NOT NULL  COMMENT '수정 시각',  -- JPA @LastModifiedDate 자동 갱신
  `deleted_at`               DATETIME      NULL      COMMENT '삭제 시각',  -- 소프트 삭제용. 조회는 NULL인 행만 씀. 현재 이 값을 채우는 코드는 없음
  PRIMARY KEY (`policy_id`)
) COMMENT = '청년 주거 정책';

CREATE TABLE `policy_region` (
  `policy_id`                VARCHAR(30)   NOT NULL  COMMENT '정책 ID',  -- policy.policy_id
  `region_code`              CHAR(5)       NOT NULL  COMMENT '시군구 코드',  -- region.region_code. API zipCd 코드, 시·도 코드(○○000)는 시군구 전체로 풀어 저장. 00000이거나 행이 없으면 전국 정책. region에 없는 코드는 건너뛰고, 하나도 못 찾으면 그 정책은 저장 안 함
  `created_at`               DATETIME      NOT NULL  COMMENT '생성 시각',  -- JPA @CreatedDate 자동 기록, 수정 불가
  `updated_at`               DATETIME      NOT NULL  COMMENT '수정 시각',  -- JPA @LastModifiedDate 자동 갱신
  `deleted_at`               DATETIME      NULL      COMMENT '삭제 시각',  -- 조회는 NULL인 행만 씀. 재수집 때는 이 칸을 쓰지 않고 그 정책의 행을 실제로 지운 뒤 다시 넣음
  PRIMARY KEY (`policy_id`, `region_code`),
  CONSTRAINT `fk_policy_region_policy` FOREIGN KEY (`policy_id`) REFERENCES `policy` (`policy_id`),
  CONSTRAINT `fk_policy_region_region` FOREIGN KEY (`region_code`) REFERENCES `region` (`region_code`)
) COMMENT = '정책 신청 지역';

CREATE TABLE `card_news` (
  `card_news_id`             BIGINT        NOT NULL  AUTO_INCREMENT COMMENT '카드뉴스 ID',  -- AUTO_INCREMENT
  `policy_id`                VARCHAR(30)   NOT NULL  COMMENT '정책 ID',  -- policy.policy_id
  `title`                    VARCHAR(255)  NULL      COMMENT '카드 제목',  -- 1번 정책명, 2번 NULL, 3번 AI 혜택 제목(없으면 '지원 혜택'), 4번 신청 기간(시작·마감일이 둘 다 있으면 'YYYY-MM-DD ~ YYYY-MM-DD', 아니면 특정기간·상시·마감). 255자 넘으면 잘라 끝에 '...'
  `body`                     VARCHAR(500)  NOT NULL  COMMENT '카드 본문',  -- AI가 쉬운 존댓말 1~2문장으로 작성. 없으면 API 원문(1·3번 지원 내용, 2번 추가 자격, 4번 신청 방법), 그것도 없으면 '등록된 내용이 없습니다.'. 500자 넘으면 잘라 끝에 '...'
  `card_no`                  BIGINT        NOT NULL  COMMENT '카드 순서',  -- 1 요약, 2 신청 대상, 3 지원 혜택, 4 신청 방법. CHECK 1~4(ck_card_news_card_no), (policy_id, card_no) UNIQUE(uk_card_news_policy_card_no)
  `created_at`               DATETIME      NOT NULL  COMMENT '생성 시각',  -- JPA @CreatedDate 자동 기록, 수정 불가
  `updated_at`               DATETIME      NOT NULL  COMMENT '수정 시각',  -- JPA @LastModifiedDate 자동 갱신
  `deleted_at`               DATETIME      NULL      COMMENT '삭제 시각',  -- 소프트 삭제용. 조회는 NULL인 행만 씀. 현재 이 값을 채우는 코드는 없음
  PRIMARY KEY (`card_news_id`),
  UNIQUE KEY `uk_card_news_policy_card_no` (`policy_id`, `card_no`),
  CONSTRAINT `ck_card_news_card_no` CHECK (`card_no` BETWEEN 1 AND 4),
  CONSTRAINT `fk_card_news_policy` FOREIGN KEY (`policy_id`) REFERENCES `policy` (`policy_id`)
) COMMENT = '카드뉴스';

CREATE TABLE `term` (
  `term_id`                  BIGINT        NOT NULL  AUTO_INCREMENT COMMENT '용어 ID',  -- AUTO_INCREMENT
  `term`                     VARCHAR(50)   NOT NULL  COMMENT '용어',  -- 정책 용어 (예: 중위소득), term-data.sql 시드로 적재
  `easy_description`         VARCHAR(500)  NOT NULL  COMMENT '쉬운 설명',  -- 쉬운 말 풀이
  `example`                  VARCHAR(300)  NULL      COMMENT '예시',  -- 정책 문구 속 사용 예시, 없으면 NULL
  `created_at`               DATETIME      NOT NULL  COMMENT '생성 시각',  -- JPA @CreatedDate 자동 기록, 수정 불가
  `updated_at`               DATETIME      NOT NULL  COMMENT '수정 시각',  -- JPA @LastModifiedDate 자동 갱신
  `deleted_at`               DATETIME      NULL      COMMENT '삭제 시각',  -- 소프트 삭제용. 조회는 NULL인 행만 씀. 현재 이 값을 채우는 코드는 없음
  PRIMARY KEY (`term_id`)
) COMMENT = '용어 풀이';

CREATE TABLE `policy_outbox` (
  `event_id`                 CHAR(36)      NOT NULL  COMMENT '이벤트 ID',  -- UUID, 구독 쪽 중복 처리 확인용
  `event_type`               VARCHAR(50)   NOT NULL  COMMENT '이벤트 종류',  -- PolicyUpdated
  `event_version`            INT           NOT NULL  DEFAULT 1 COMMENT '이벤트 버전',  -- 1부터
  `aggregate_id`             VARCHAR(30)   NOT NULL  COMMENT '정책 ID',  -- policy_id 값 (FK 없음, 정책 삭제와 무관하게 보관)
  `payload`                  JSON          NOT NULL  COMMENT '본문',  -- policyId, policyName, categories, supportContent, applyEndDate ...
  `status`                   VARCHAR(20)   NOT NULL  DEFAULT 'PENDING' COMMENT '발행 상태',  -- PENDING, PUBLISHED, FAILED
  `retry_count`              INT           NOT NULL  DEFAULT 0 COMMENT '재시도 횟수',  -- 기본 0
  `occurred_at`              DATETIME      NOT NULL  COMMENT '발생 시각',  -- 정책 저장과 같은 트랜잭션에서 기록
  `published_at`             DATETIME      NULL      COMMENT '발행 시각',  -- Kafka 전송 성공 시각
  PRIMARY KEY (`event_id`),
  KEY `idx_policy_outbox_status_occurred` (`status`, `occurred_at`)
) COMMENT = '정책 이벤트 발행 대기';
