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
import com.hyeja.policyservice.policy.converter.PolicyMemberConverter;
import com.hyeja.policyservice.policy.dto.PolicyMemberResponseDTO.PolicyListDTO;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicySort;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.region.entity.Region;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 로그인 회원 기능(내 조건 판정, 하트) 담당 서비스입니다.
// 판정과 회원 목록은 user(F6) 회원 조건으로 1차와 같게 합니다. 하트는 engagement(F4) 연결 전까지 false입니다.
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyMemberService {

    private final MemberProfileReader memberProfileReader;
    private final PolicyRepository policyRepository;
    private final PolicyRegionRepository policyRegionRepository;
    private final PolicyEligibilityEvaluator policyEligibilityEvaluator;
    // 한국 시간(Asia/Seoul) 기준 오늘 날짜. ClockConfig의 koreaClock 빈입니다.
    private final Clock clock;

    // 로그인 회원 주거 정책 목록(/housing/me). 1차 PolicyService.getHousingPoliciesForMember와 같은 조건입니다.
    // onlyEligible이면 나이·무주택·취업·지역(시군구 일치 또는 전국)으로 거릅니다. 하트는 F4 연결 전까지 false입니다.
    // 탈퇴 회원 MEMBER_001, 조건 없음 PROFILE_001은 그대로 던지고, user가 응답하지 않으면 F6 계약대로 비로그인 목록으로 대신합니다.
    // user 응답을 기다리는 동안 DB 커넥션을 잡지 않도록 트랜잭션을 걸지 않습니다(조회는 각 리포지토리 호출 단위).
    public PolicyListDTO getHousingPoliciesForMember(
            Long memberId, PolicyCategory category, PolicySort sort, boolean onlyEligible, int page, int size) {
        LocalDate today = LocalDate.now(clock);
        PageRequest pageRequest = PageRequest.of(page, size, sort.toSort());
        String categoryName = category == null ? null : category.name();
        Page<Policy> policyPage;
        try {
            MemberProfileDTO profile = memberProfileReader.getActiveProfile(memberId);
            policyPage = policyRepository.findHousingPoliciesForMember(
                    categoryName,
                    onlyEligible,
                    today,
                    profile.birth() == null ? 0 : Period.between(profile.birth(), today).getYears(),
                    Boolean.TRUE.equals(profile.houselessYn()),
                    profile.employmentCode() == null ? "" : profile.employmentCode().name(),
                    profile.region() == null ? "" : profile.region().regionCode(),
                    pageRequest);
        } catch (GeneralException e) {
            if (e.getCode() != ErrorStatus.USER_SERVICE_UNAVAILABLE) {
                throw e;
            }
            log.warn("회원 조건을 불러오지 못해 비로그인 목록으로 응답합니다. memberId={}", memberId);
            policyPage = policyRepository.findGuestHousingPolicies(categoryName, today, pageRequest);
        }

        List<String> policyIds = policyPage.getContent().stream().map(Policy::getPolicyId).toList();
        Map<String, List<Region>> regionsByPolicyId = policyIds.isEmpty()
                ? Collections.emptyMap()
                : policyRegionRepository.findAllActiveByPolicyIds(policyIds).stream()
                        .collect(Collectors.groupingBy(
                                policyRegion -> policyRegion.getPolicy().getPolicyId(),
                                Collectors.mapping(PolicyRegion::getRegion, Collectors.toList())));
        return PolicyMemberConverter.toPolicyListDTO(policyPage, regionsByPolicyId, Set.of(), today);
    }

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
