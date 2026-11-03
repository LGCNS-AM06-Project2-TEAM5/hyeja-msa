package com.hyeja.policyservice.internal.service;

import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.internal.dto.PolicySnapshotDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicySnapshotService {
    private final PolicyRepository policyRepository;

    public PolicySnapshotDTO getSnapshot(String policyId) {
        // 1차 FavoriteService.createFavorite의 정책 검증 기준입니다. 신청 기간은 검사하지 않습니다.
        Policy policy = policyRepository.findById(policyId)
                .filter(value -> !value.isDeleted() && Boolean.TRUE.equals(value.getActiveYn()))
                .orElseThrow(() -> new GeneralException(ErrorStatus.POLICY_NOT_FOUND));
        return PolicySnapshotDTO.from(policy);
    }
}
