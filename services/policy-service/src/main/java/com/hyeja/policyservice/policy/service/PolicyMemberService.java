package com.hyeja.policyservice.policy.service;

import com.hyeja.policyservice.client.user.MemberProfileReader;
import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO;
import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO.ConditionResultDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.EligibilityStatus;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 로그인 회원 기능(내 조건 판정, 하트) 담당 서비스입니다.
// 판정은 user(F6) 회원 조건으로 1차와 같게 합니다. 하트는 engagement(F4) 연결 전까지 false입니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyMemberService {

    private final MemberProfileReader memberProfileReader;
    private final PolicyRegionRepository policyRegionRepository;
    private final PolicyEligibilityEvaluator policyEligibilityEvaluator;

    // 정책 상세에서 memberId가 있을 때 호출합니다. 비로그인이면 null을 넘겨도 됩니다.
    // 탈퇴 회원 MEMBER_001, 조건 없음 PROFILE_001은 1차처럼 그대로 던지고,
    // user-service가 응답하지 않으면 상세 자체는 보여 주도록 비로그인 응답으로 대신합니다.
    public PolicyMemberDetailDTO getMemberDetail(Policy policy, Long memberId) {
        if (memberId == null) {
            return PolicyMemberDetailDTO.guest();
        }
        MemberProfileDTO profile;
        try {
            profile = memberProfileReader.getActiveProfile(memberId);
        } catch (GeneralException e) {
            if (e.getCode() != ErrorStatus.USER_SERVICE_UNAVAILABLE) {
                throw e;
            }
            log.warn("회원 조건을 불러오지 못해 비로그인 상세로 응답합니다. policyId={}, memberId={}",
                    policy.getPolicyId(), memberId);
            return PolicyMemberDetailDTO.guest();
        }
        List<PolicyRegion> policyRegions =
                policyRegionRepository.findAllByPolicy_PolicyId(policy.getPolicyId());
        List<ConditionResultDTO> conditions =
                policyEligibilityEvaluator.evaluate(policy, profile, policyRegions);
        return new PolicyMemberDetailDTO(false, overallStatus(conditions), conditions);
    }

    // 1차 PolicyService.overallStatus와 같습니다: 하나라도 불가면 불가, 그다음 확인 필요, 나머지는 가능.
    private EligibilityStatus overallStatus(List<ConditionResultDTO> conditions) {
        if (conditions.stream().anyMatch(condition -> condition.status() == EligibilityStatus.DISABLE)) {
            return EligibilityStatus.DISABLE;
        }
        if (conditions.stream().anyMatch(condition -> condition.status() == EligibilityStatus.UNKNOWN)) {
            return EligibilityStatus.UNKNOWN;
        }
        return EligibilityStatus.ABLE;
    }
}
