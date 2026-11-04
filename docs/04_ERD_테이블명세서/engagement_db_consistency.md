# engagement_db ERD·테이블 명세 정합성 검토

## 검토 기준

- 기준일: 2026-11-04
- 구현 기준: `services/engagement-service/src/main/java`의 JPA 매핑
- 비교 문서: `engagement_db_erd.md`, `engagement_db.sql`
- 검토 대상: 테이블명, 컬럼명·타입·길이, PK, FK, 유니크 제약, NULL 허용 여부

## 엔티티-문서 매핑

| JPA 엔티티 | 물리 테이블 | ERD | 테이블 명세 | 결과 |
|---|---|:---:|:---:|:---:|
| `Policy` | `policy_snapshot` | 반영 | 반영 | 일치 |
| `Favorite` | `favorite` | 반영 | 반영 | 일치 |
| `Notification` | `notification` | 반영 | 반영 | 일치 |
| `ProcessedPolicyEvent` | `processed_policy_event` | 반영 | 반영 | 일치 |
| `ProcessedMemberEvent` | `processed_member_event` | 반영 | 반영 | 일치 |
| `FailedMemberEvent` | `failed_member_event` | 반영 | 반영 | 일치 |
| `DltAlertState` | `dlt_alert_state` | 반영 | 반영 | 일치 |

`BaseEntity`는 독립 테이블이 아닌 `@MappedSuperclass`이므로 테이블 수에서 제외하고, 이를 상속하는 `policy_snapshot`, `favorite`, `notification`에 `created_at`, `updated_at`, `deleted_at`을 반영했습니다.

## 관계·제약 대조

| 항목 | JPA 매핑 | ERD·명세 반영 | 결과 |
|---|---|---|:---:|
| 관심 정책 중복 방지 | `uk_favorite_member_policy(member_id, policy_id)` | 동일 이름·컬럼의 UNIQUE | 일치 |
| 마감 알림 중복 방지 | `uk_notification_member_policy_deadline(member_id, policy_id, deadline_date)` | 동일 이름·컬럼의 UNIQUE | 일치 |
| DLT 레코드 중복 방지 | `uk_failed_member_event_dlt_position(dlt_topic, dlt_partition, dlt_offset)` | 동일 이름·컬럼의 UNIQUE | 일치 |
| 관심→정책 스냅샷 | `fk_favorite_policy` | `favorite.policy_id` 물리 FK | 일치 |
| 알림→정책 스냅샷 | `fk_notification_policy` | `notification.policy_id` 물리 FK | 일치 |
| 회원 참조 | `member_id` 기본 컬럼 | 논리 참조, 물리 FK 없음 | 일치 |
| 이벤트 처리 멱등성 | 각 `event_id`를 PK로 사용 | 각 처리 이력 테이블 PK로 반영 | 일치 |

## 타입·NULL 정책 대조

- Java `Long`/`long` 식별값과 오프셋은 `BIGINT`, `Integer`/`int`는 `INT`로 반영했습니다.
- `LocalDate`는 `DATE`, `LocalDateTime`은 마이크로초 정밀도의 `DATETIME(6)`으로 명세했습니다.
- `Boolean`/`boolean`은 Hibernate 7.4.5 MariaDB 방언의 신규 스키마 생성 타입에 맞춰 `BIT(1)`로 반영했습니다.
- `@Lob String`인 `payload`, `latest_payload`는 MariaDB 문자열 LOB인 `LONGTEXT`로 명세했습니다.
- `@Column(nullable = false)` 및 원시 타입 필드는 `NOT NULL`, 그 외 선택 값은 `NULL`로 반영했습니다.
- `Policy.categories`는 `PolicyCategoryConverter`가 단일 문자열 컬럼으로 변환하므로 관계 테이블 없이 `VARCHAR(255)`로 반영했습니다.
- `Policy.applyPeriodCode`는 Hibernate 7.4.5 MariaDB 방언의 `EnumType.STRING` 생성 타입에 맞춰 `ENUM('SPECIFIC_PERIOD', 'ALWAYS', 'CLOSED')`로 반영했습니다.

## 경계와 운영상 주의점

1. `policy_snapshot`은 `policy_db.policy`의 물리 복제나 DB 간 FK가 아니라 이벤트·내부 API로 갱신되는 engagement 소유 읽기 모델입니다.
2. `processed_policy_event.policy_id`는 이벤트 추적값으로 남기므로 `policy_snapshot`에 FK를 두지 않습니다. 스냅샷 삭제 여부와 무관하게 처리 이력을 보존할 수 있습니다.
3. `member_id`는 user-service 데이터의 논리 참조입니다. 회원 탈퇴 시 `member-withdrawn` 이벤트로 관심·알림 데이터를 정리하며 DB 간 cascade에 의존하지 않습니다.
4. 현재 개발 설정은 Hibernate `ddl-auto: update`이므로 본 SQL은 문서 명세입니다. 운영 마이그레이션 스크립트로 사용할 때는 실제 DB에서 생성된 DDL과 재대조해야 합니다.

## 검토 결론

구현 기준 7개 테이블이 ERD와 테이블 명세에 모두 포함되어 있으며, 현재 JPA 매핑과 문서 사이에서 누락되거나 충돌하는 테이블·키·제약은 확인되지 않았습니다.
