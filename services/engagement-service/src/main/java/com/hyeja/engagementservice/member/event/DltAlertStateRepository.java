package com.hyeja.engagementservice.member.event;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DltAlertStateRepository extends JpaRepository<DltAlertState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from DltAlertState state where state.dltTopic = :topic")
    Optional<DltAlertState> findByTopicForUpdate(@Param("topic") String topic);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<DltAlertState> findAllByNotificationPendingTrue();
}
