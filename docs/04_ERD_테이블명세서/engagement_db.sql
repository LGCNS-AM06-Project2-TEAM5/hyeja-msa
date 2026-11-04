-- engagement-service · engagement_db
-- 기준: engagement-service JPA 엔티티(2026-11-04)
-- member_id는 user-service 회원의 논리 참조이며 서비스 간 물리 FK를 생성하지 않습니다.
-- policy_snapshot은 engagement-service가 소유하는 정책 읽기 모델입니다.
-- COMMENT = 컬럼/테이블 설명, 인라인 주석 = 제약과 운영 의미

CREATE TABLE `policy_snapshot` (
  `policy_id`                VARCHAR(100)  NOT NULL COMMENT '정책 ID',
  `policy_name`              VARCHAR(200)  NOT NULL COMMENT '정책명',
  `categories`               VARCHAR(255)  NULL     COMMENT '정책 카테고리 집합', -- PolicyCategoryConverter가 문자열로 변환
  `support_content`          TEXT          NULL     COMMENT '지원 내용',
  `apply_end_date`           DATE          NULL     COMMENT '신청 마감일',
  `apply_period_code`        ENUM('SPECIFIC_PERIOD', 'ALWAYS', 'CLOSED') NULL COMMENT '신청 기간 구분', -- PolicyApplyPeriod enum 이름
  `apply_url`                VARCHAR(500)  NULL     COMMENT '신청 URL',
  `active_yn`                BIT(1)        NULL     COMMENT '활성 여부',
  `created_at`               DATETIME(6)   NOT NULL COMMENT '생성 시각',
  `updated_at`               DATETIME(6)   NOT NULL COMMENT '수정 시각',
  `deleted_at`               DATETIME(6)   NULL     COMMENT '삭제 시각',
  PRIMARY KEY (`policy_id`)
) COMMENT = '정책 서비스 원본의 로컬 읽기 모델';

CREATE TABLE `favorite` (
  `favorite_id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '관심 ID',
  `member_id`                BIGINT        NOT NULL COMMENT '회원 ID', -- user-service 논리 참조, 물리 FK 없음
  `policy_id`                VARCHAR(100)  NOT NULL COMMENT '정책 ID',
  `created_at`               DATETIME(6)   NOT NULL COMMENT '생성 시각',
  `updated_at`               DATETIME(6)   NOT NULL COMMENT '수정 시각',
  `deleted_at`               DATETIME(6)   NULL     COMMENT '삭제 시각',
  PRIMARY KEY (`favorite_id`),
  UNIQUE KEY `uk_favorite_member_policy` (`member_id`, `policy_id`),
  KEY `idx_favorite_policy_id` (`policy_id`),
  CONSTRAINT `fk_favorite_policy`
    FOREIGN KEY (`policy_id`) REFERENCES `policy_snapshot` (`policy_id`)
) COMMENT = '회원 관심 정책';

CREATE TABLE `notification` (
  `notification_id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '알림 ID',
  `member_id`                BIGINT        NOT NULL COMMENT '회원 ID', -- user-service 논리 참조, 물리 FK 없음
  `policy_id`                VARCHAR(100)  NOT NULL COMMENT '정책 ID',
  `read_yn`                  BIT(1)        NOT NULL COMMENT '읽음 여부',
  `deadline_date`            DATE          NOT NULL COMMENT '알림 기준 마감일',
  `created_at`               DATETIME(6)   NOT NULL COMMENT '생성 시각',
  `updated_at`               DATETIME(6)   NOT NULL COMMENT '수정 시각',
  `deleted_at`               DATETIME(6)   NULL     COMMENT '삭제 시각',
  PRIMARY KEY (`notification_id`),
  UNIQUE KEY `uk_notification_member_policy_deadline`
    (`member_id`, `policy_id`, `deadline_date`),
  KEY `idx_notification_policy_id` (`policy_id`),
  CONSTRAINT `fk_notification_policy`
    FOREIGN KEY (`policy_id`) REFERENCES `policy_snapshot` (`policy_id`)
) COMMENT = '관심 정책 마감 알림';

CREATE TABLE `processed_policy_event` (
  `event_id`                 VARCHAR(36)   NOT NULL COMMENT '정책 이벤트 ID',
  `policy_id`                VARCHAR(100)  NOT NULL COMMENT '정책 ID', -- 이벤트가 가리킨 식별값이며 FK 없음
  `processed_at`             DATETIME(6)   NOT NULL COMMENT '처리 완료 시각',
  PRIMARY KEY (`event_id`)
) COMMENT = 'policy-updated 이벤트 멱등 처리 기록';

CREATE TABLE `processed_member_event` (
  `event_id`                 VARCHAR(100)  NOT NULL COMMENT '회원 이벤트 ID',
  `event_type`               VARCHAR(100)  NOT NULL COMMENT '회원 이벤트 유형',
  `member_id`                BIGINT        NOT NULL COMMENT '회원 ID', -- 이벤트 값, 물리 FK 없음
  `processed_at`             DATETIME(6)   NOT NULL COMMENT '처리 완료 시각',
  PRIMARY KEY (`event_id`)
) COMMENT = '회원 이벤트 멱등 처리 기록';

CREATE TABLE `failed_member_event` (
  `failed_event_id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '실패 이벤트 ID',
  `dlt_topic`                VARCHAR(200)  NOT NULL COMMENT 'DLT 토픽명',
  `dlt_partition`            INT           NOT NULL COMMENT 'DLT 파티션 번호',
  `dlt_offset`               BIGINT        NOT NULL COMMENT 'DLT 오프셋',
  `event_key`                VARCHAR(200)  NULL     COMMENT 'Kafka 이벤트 키',
  `payload`                  LONGTEXT      NULL     COMMENT '실패 이벤트 본문',
  `original_topic`           VARCHAR(200)  NULL     COMMENT '원본 토픽명',
  `original_partition`       INT           NULL     COMMENT '원본 파티션 번호',
  `original_offset`          BIGINT        NULL     COMMENT '원본 오프셋',
  `exception_message`        VARCHAR(1000) NULL     COMMENT '예외 메시지',
  `received_at`              DATETIME(6)   NOT NULL COMMENT 'DLT 수신 시각',
  `resolved_at`              DATETIME(6)   NULL     COMMENT '처리 완료 시각',
  PRIMARY KEY (`failed_event_id`),
  UNIQUE KEY `uk_failed_member_event_dlt_position`
    (`dlt_topic`, `dlt_partition`, `dlt_offset`)
) COMMENT = 'member-withdrawn DLT 실패 이력';

CREATE TABLE `dlt_alert_state` (
  `dlt_topic`                VARCHAR(200)  NOT NULL COMMENT 'DLT 토픽명',
  `failure_count`            INT           NOT NULL COMMENT '알림 이후 누적 실패 건수',
  `notification_pending`     BIT(1)        NOT NULL COMMENT '관리자 알림 대기 여부',
  `latest_event_key`         VARCHAR(200)  NULL     COMMENT '최근 실패 이벤트 키',
  `latest_payload`           LONGTEXT      NULL     COMMENT '최근 실패 이벤트 본문',
  `updated_at`               DATETIME(6)   NOT NULL COMMENT '상태 수정 시각',
  PRIMARY KEY (`dlt_topic`)
) COMMENT = 'DLT 관리자 알림 집계 상태';
