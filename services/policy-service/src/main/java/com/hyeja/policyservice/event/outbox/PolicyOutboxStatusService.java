package com.hyeja.policyservice.event.outbox;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyOutboxStatusService {

    private final PolicyOutboxRepository repository;

    // 대기·발행 건수, 마지막 발행 시각, 최근 10건(새것부터)을 한 번에 돌려줍니다.
    @Transactional(readOnly = true)
    public PolicyOutboxStatusDTO getStatus() {
        LocalDateTime lastPublishedAt = repository.findTopByPublishedAtIsNotNullOrderByPublishedAtDesc()
                .map(PolicyOutbox::getPublishedAt)
                .orElse(null);
        return new PolicyOutboxStatusDTO(
                repository.countByPublishedAtIsNull(),
                repository.countByPublishedAtIsNotNull(),
                lastPublishedAt,
                repository.findTop10ByOrderByIdDesc().stream()
                        .map(PolicyOutboxStatusDTO.RecentEvent::from)
                        .toList());
    }
}
