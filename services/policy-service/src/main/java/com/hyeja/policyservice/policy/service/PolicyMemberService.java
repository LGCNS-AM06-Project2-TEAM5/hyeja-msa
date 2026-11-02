package com.hyeja.policyservice.policy.service;

import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import org.springframework.stereotype.Service;

// 로그인 회원 기능(내 조건 판정, 하트) 담당 서비스입니다.
// 지금은 연결 지점만 있습니다. user(F6)·engagement(F4) 호출과 판정은 3단계에서 채웁니다.
@Service
public class PolicyMemberService {

    // 정책 상세에서 memberId가 있을 때 호출합니다. 비로그인이면 null을 넘겨도 됩니다.
    public PolicyMemberDetailDTO getMemberDetail(Policy policy, Long memberId) {
        return PolicyMemberDetailDTO.guest();
    }
}
