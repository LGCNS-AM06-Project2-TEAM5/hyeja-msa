package com.hyeja.policyservice.event.outbox;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyOutboxRepository extends JpaRepository<PolicyOutbox, Long> {

    // 아직 보내지 않은 행을 적은 순서대로 가져옵니다.
    List<PolicyOutbox> findTop100ByPublishedAtIsNullOrderByIdAsc();

    // 관리자 화면의 이벤트 동기화 상태용 조회입니다.
    long countByPublishedAtIsNull();

    long countByPublishedAtIsNotNull();

    Optional<PolicyOutbox> findTopByPublishedAtIsNotNullOrderByPublishedAtDesc();

    List<PolicyOutbox> findTop10ByOrderByIdDesc();
}
