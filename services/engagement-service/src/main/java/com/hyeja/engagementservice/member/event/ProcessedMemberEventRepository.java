package com.hyeja.engagementservice.member.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMemberEventRepository extends JpaRepository<ProcessedMemberEvent, String> {

    boolean existsByEventId(String eventId);
}
