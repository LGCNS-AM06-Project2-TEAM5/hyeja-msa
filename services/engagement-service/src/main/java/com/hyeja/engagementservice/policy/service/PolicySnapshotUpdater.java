package com.hyeja.engagementservice.policy.service;

import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.engagementservice.policy.enums.PolicyCategory;
import com.hyeja.engagementservice.policy.event.PolicyUpdatedEvent;
import com.hyeja.engagementservice.policy.event.ProcessedPolicyEvent;
import com.hyeja.engagementservice.policy.event.ProcessedPolicyEventRepository;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * policy_snapshot(engagement 읽기 모델)을 policy 원본 값으로 맞춥니다.
 * policy-updated 구독(apply)과 F5로 받은 정책 저장(upsert)이 같이 씁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicySnapshotUpdater {
    private final PolicyRepository policyRepository;
    private final ProcessedPolicyEventRepository processedEvents;

    /** 처음 보는 이벤트면 스냅샷에 반영하고 true, 이미 반영한 eventId면 아무것도 하지 않고 false를 돌려줍니다. */
    @Transactional
    public boolean apply(PolicyUpdatedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("이미 반영한 policy-updated라 건너뜁니다. eventId={}, policyId={}", event.eventId(), event.policyId());
            return false;
        }
        if (event.deleted()) {
            policyRepository.findById(event.policyId()).ifPresent(Policy::softDelete);
        } else {
            upsert(event.policyId(), event.policyName(), event.categories(), event.supportContent(),
                    event.applyEndDate(), event.applyPeriodCode(), event.applyUrl(), event.activeYn());
        }
        processedEvents.save(new ProcessedPolicyEvent(event.eventId(), event.policyId(), LocalDateTime.now()));
        return true;
    }

    @Transactional
    public Policy upsert(String policyId, String policyName, Set<PolicyCategory> categories, String supportContent,
                         LocalDate applyEndDate, PolicyApplyPeriod applyPeriodCode, String applyUrl, Boolean activeYn) {
        Policy policy = policyRepository.findById(policyId).orElseGet(Policy::new);
        policy.setPolicyId(policyId);
        policy.setPolicyName(policyName);
        policy.setCategories(categories);
        policy.setSupportContent(supportContent);
        policy.setApplyEndDate(applyEndDate);
        policy.setApplyPeriodCode(applyPeriodCode);
        policy.setApplyUrl(applyUrl);
        policy.setActiveYn(activeYn);
        return policyRepository.save(policy);
    }
}
