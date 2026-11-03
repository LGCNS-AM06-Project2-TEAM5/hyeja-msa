package com.hyeja.engagementservice.policy.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedPolicyEventRepository extends JpaRepository<ProcessedPolicyEvent, String> {}
