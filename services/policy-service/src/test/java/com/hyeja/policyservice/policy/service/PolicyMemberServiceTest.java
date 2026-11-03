package com.hyeja.policyservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hyeja.policyservice.client.user.MemberProfileReader;
import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO;
import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO.ConditionResultDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.EligibilityConditionType;
import com.hyeja.policyservice.policy.enums.EligibilityStatus;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyMemberServiceTest {

    private final MemberProfileReader memberProfileReader = mock(MemberProfileReader.class);
    private final PolicyRegionRepository policyRegionRepository = mock(PolicyRegionRepository.class);
    private final PolicyEligibilityEvaluator evaluator = mock(PolicyEligibilityEvaluator.class);
    private final PolicyMemberService service =
            new PolicyMemberService(memberProfileReader, policyRegionRepository, evaluator);

    private final Policy policy = policy("P1");
    private final MemberProfileDTO profile = mock(MemberProfileDTO.class);

    @Test
    void 회원_조건으로_판정하고_하나라도_불가면_전체_불가() {
        List<PolicyRegion> regions = List.of(mock(PolicyRegion.class));
        given(memberProfileReader.getActiveProfile(7L)).willReturn(profile);
        given(policyRegionRepository.findAllByPolicy_PolicyId("P1")).willReturn(regions);
        List<ConditionResultDTO> conditions = conditions(
                EligibilityStatus.ABLE, EligibilityStatus.UNKNOWN, EligibilityStatus.DISABLE);
        given(evaluator.evaluate(policy, profile, regions)).willReturn(conditions);

        PolicyMemberDetailDTO detail = service.getMemberDetail(policy, 7L);

        assertThat(detail.conditions()).isEqualTo(conditions);
        assertThat(detail.overallStatus()).isEqualTo(EligibilityStatus.DISABLE);
        assertThat(detail.isFavorite()).isFalse();
    }

    @Test
    void 불가가_없고_확인_필요가_있으면_전체_확인_필요() {
        givenConditions(EligibilityStatus.ABLE, EligibilityStatus.UNKNOWN);

        assertThat(service.getMemberDetail(policy, 7L).overallStatus())
                .isEqualTo(EligibilityStatus.UNKNOWN);
    }

    @Test
    void 모두_가능하면_전체_가능() {
        givenConditions(EligibilityStatus.ABLE, EligibilityStatus.ABLE);

        assertThat(service.getMemberDetail(policy, 7L).overallStatus())
                .isEqualTo(EligibilityStatus.ABLE);
    }

    @Test
    void user_서비스가_응답하지_않으면_비로그인_응답으로_대신한다() {
        given(memberProfileReader.getActiveProfile(7L))
                .willThrow(new GeneralException(ErrorStatus.USER_SERVICE_UNAVAILABLE));

        PolicyMemberDetailDTO detail = service.getMemberDetail(policy, 7L);

        assertThat(detail).isEqualTo(PolicyMemberDetailDTO.guest());
        verifyNoInteractions(evaluator);
    }

    @Test
    void 조건이_없으면_1차처럼_PROFILE_001을_그대로_던진다() {
        given(memberProfileReader.getActiveProfile(7L))
                .willThrow(new GeneralException(ErrorStatus.PROFILE_NOT_FOUND));

        assertThatThrownBy(() -> service.getMemberDetail(policy, 7L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getCode())
                .isEqualTo(ErrorStatus.PROFILE_NOT_FOUND);
    }

    @Test
    void 회원_ID가_없으면_user를_부르지_않는다() {
        assertThat(service.getMemberDetail(policy, null)).isEqualTo(PolicyMemberDetailDTO.guest());
        verifyNoInteractions(memberProfileReader, evaluator);
    }

    private void givenConditions(EligibilityStatus... statuses) {
        given(memberProfileReader.getActiveProfile(7L)).willReturn(profile);
        given(policyRegionRepository.findAllByPolicy_PolicyId("P1")).willReturn(List.of());
        given(evaluator.evaluate(any(), any(), any())).willReturn(conditions(statuses));
    }

    private static List<ConditionResultDTO> conditions(EligibilityStatus... statuses) {
        return java.util.Arrays.stream(statuses)
                .map(status -> new ConditionResultDTO(EligibilityConditionType.AGE, status, "조건", "값"))
                .toList();
    }

    private static Policy policy(String policyId) {
        Policy policy = mock(Policy.class);
        given(policy.getPolicyId()).willReturn(policyId);
        return policy;
    }
}
