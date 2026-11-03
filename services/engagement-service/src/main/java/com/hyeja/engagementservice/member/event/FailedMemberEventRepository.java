package com.hyeja.engagementservice.member.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedMemberEventRepository extends JpaRepository<FailedMemberEvent, Long> {

    boolean existsByDltTopicAndDltPartitionAndDltOffset(String dltTopic, int dltPartition, long dltOffset);

    long countByResolvedAtIsNull();
}
