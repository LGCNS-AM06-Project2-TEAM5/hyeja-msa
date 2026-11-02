package com.hyeja.policyservice.policy.controller;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.hyeja.policyservice.policy.dto.PolicyGuestResponseDTO;
import com.hyeja.policyservice.policy.dto.PolicyDetailResponseDTO;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicySort;
import com.hyeja.policyservice.policy.service.PolicyService;
import com.hyeja.policyservice.global.exception.ExceptionAdvice;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PolicyQueryControllerTest {
    private final PolicyService policyService = mock(PolicyService.class);
    private MockMvc mvc;
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PolicyController(policyService))
                .setControllerAdvice(new ExceptionAdvice()).build();
    }

    @Test
    void returnsGuestHousingPolicyPage() throws Exception {
        PolicyGuestResponseDTO.PolicyListItemDTO policy =
                PolicyGuestResponseDTO.PolicyListItemDTO.builder()
                        .policyId("POLICY-1")
                        .policyName("청년 월세 지원")
                        .categoryCodes(Set.of(PolicyCategory.MONTHLY_RENT))
                        .categoryNames(List.of("월세"))
                        .regions(List.of(PolicyGuestResponseDTO.PolicyRegionItemDTO.builder()
                                .regionCode("11440")
                                .regionName("서울특별시 마포구")
                                .build()))
                        .nationwide(false)
                        .applyEndDate(LocalDate.of(2026, 9, 30))
                        .applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD)
                        .dDay(3)
                        .build();
        when(policyService.getGuestHousingPolicies(
                PolicyCategory.MONTHLY_RENT, PolicySort.VIEW_COUNT, 1, 8))
                .thenReturn(PolicyGuestResponseDTO.PolicyListDTO.builder()
                        .policies(List.of(policy))
                        .page(1)
                        .size(8)
                        .totalElements(9)
                        .totalPages(2)
                        .hasNext(false)
                        .build());

        mvc.perform(get("/api/policies/housing")
                        .param("category", "MONTHLY_RENT")
                        .param("sort", "VIEW_COUNT")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result.page").value(1))
                .andExpect(jsonPath("$.result.size").value(8))
                .andExpect(jsonPath("$.result.totalElements").value(9))
                .andExpect(jsonPath("$.result.policies[0].policy_id").value("POLICY-1"))
                .andExpect(jsonPath("$.result.policies[0].category_codes[0]").value("MONTHLY_RENT"))
                .andExpect(jsonPath("$.result.policies[0].regions[0].region_code").value("11440"))
                .andExpect(jsonPath("$.result.policies[0].apply_period_code").value("SPECIFIC_PERIOD"))
                .andExpect(jsonPath("$.result.policies[0].d_day").value(3))
                .andExpect(jsonPath("$.result.policies[0].dday").doesNotExist())
                .andExpect(jsonPath("$.result.policies[0].favorite_yn").doesNotExist());

        verify(policyService).getGuestHousingPolicies(
                PolicyCategory.MONTHLY_RENT, PolicySort.VIEW_COUNT, 1, 8);
    }

    @Test
    void usesDefaultGuestListConditions() throws Exception {
        when(policyService.getGuestHousingPolicies(null, PolicySort.DEADLINE, 0, 8))
                .thenReturn(PolicyGuestResponseDTO.PolicyListDTO.builder()
                        .policies(List.of())
                        .page(0)
                        .size(8)
                        .totalElements(0)
                        .totalPages(0)
                        .hasNext(false)
                        .build());

        mvc.perform(get("/api/policies/housing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.policies").isEmpty())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(8));

        verify(policyService).getGuestHousingPolicies(null, PolicySort.DEADLINE, 0, 8);
    }

    @Test
    void returnsBadRequestForUnknownGuestSort() throws Exception {
        mvc.perform(get("/api/policies/housing").param("sort", "POPULAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void returnsBadRequestForInvalidGuestPageConditions() throws Exception {
        mvc.perform(get("/api/policies/housing").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        mvc.perform(get("/api/policies/housing").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        mvc.perform(get("/api/policies/housing").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void returnsBadRequestForUnknownCategoryAndNonNumericPage() throws Exception {
        mvc.perform(get("/api/policies/housing").param("category", "UNKNOWN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_001"));
        mvc.perform(get("/api/policies/housing").param("page", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(policyService);
    }

    @Test
    void returnsGuestDetailWithOriginalJsonNamesAndNullStatus() throws Exception {
        when(policyService.getPolicyDetailForMember("detail", null)).thenReturn(detail());
        mvc.perform(get("/api/policies/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.policyId").value("detail"))
                .andExpect(jsonPath("$.result.applyPeriod").value("ALWAYS"))
                .andExpect(jsonPath("$.result.isFavorite").value(false))
                .andExpect(jsonPath("$.result.favorite").doesNotExist())
                .andExpect(jsonPath("$.result.overallStatus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.result.conditions").isEmpty());
        verify(policyService).getPolicyDetailForMember("detail", null);
    }

    @Test
    void returns404ForMissingPolicy() throws Exception {
        when(policyService.getPolicyDetailForMember("missing", null))
                .thenThrow(new GeneralException(ErrorStatus.POLICY_NOT_FOUND));
        mvc.perform(get("/api/policies/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_001"));
    }

    @Test
    void forwardsVerifiedMemberIdToDetailService() throws Exception {
        when(policyService.getPolicyDetailForMember("detail", 42L)).thenReturn(detail());
        mvc.perform(get("/api/policies/detail").header("X-Member-Id", "42")
                        .header("Authorization", "Bearer verified-by-gateway"))
                .andExpect(status().isOk());
        verify(policyService).getPolicyDetailForMember("detail", 42L);
    }

    @Test
    void rejectsAuthorizationWithoutIdentityAndInvalidIdentity() throws Exception {
        mvc.perform(get("/api/policies/detail").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMON_002"));
        for (String id : List.of("abc", "0", "-1", "9223372036854775808")) {
            mvc.perform(get("/api/policies/detail").header("X-Member-Id", id))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(policyService);
    }

    private PolicyDetailResponseDTO detail() {
        return new PolicyDetailResponseDTO("detail", "정책", Set.of(PolicyCategory.OTHER), List.of("기타 주거"),
                null, null, null, null, null, List.of(), PolicyApplyPeriod.ALWAYS, "상시",
                null, null, null, null, null, true, false, null, List.of());
    }
}
