package com.hyeja.policyservice.event.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyOutboxRepository extends JpaRepository<PolicyOutbox, Long> {
}
