package com.hyeja.policyservice.internal;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PolicySnapshotIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private PolicyRepository policies;
    @Autowired private PolicyRegionRepository links;
    @Autowired private CardNewsRepository cards;
    @Autowired private EntityManager em;

    @BeforeEach
    void clearPolicies() {
        cards.deleteAll();
        links.deleteAll();
        policies.deleteAll();
    }

    @Test
    void returnsAgreedSnapshotFieldsWithoutMemberHeader() throws Exception {
        save(policy("snapshot").categories(Set.of(PolicyCategory.MONTHLY_RENT, PolicyCategory.JEONSE))
                .supportContent("월세 지원 내용").applyEndDate(LocalDate.of(2026, 11, 30))
                .applyUrl("https://example.com/apply"));

        mvc.perform(get("/internal/policies/snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.message").value("성공입니다."))
                .andExpect(jsonPath("$.result.length()").value(9))
                .andExpect(jsonPath("$.result.policyId").value("snapshot"))
                .andExpect(jsonPath("$.result.policyName").value("청년 지원"))
                .andExpect(jsonPath("$.result.categories", containsInAnyOrder("MONTHLY_RENT", "JEONSE")))
                .andExpect(jsonPath("$.result.supportContent").value("월세 지원 내용"))
                .andExpect(jsonPath("$.result.applyEndDate").value("2026-11-30"))
                .andExpect(jsonPath("$.result.applyPeriodCode").value("SPECIFIC_PERIOD"))
                .andExpect(jsonPath("$.result.applyUrl").value("https://example.com/apply"))
                .andExpect(jsonPath("$.result.activeYn").value(true))
                .andExpect(jsonPath("$.result.deleted").value(false));
    }

    @Test
    void preservesNullFieldsAndAlwaysOpenEnum() throws Exception {
        save(policy("nullable").applyPeriodCode(PolicyApplyPeriod.ALWAYS));
        mvc.perform(get("/internal/policies/nullable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(9))
                .andExpect(jsonPath("$.result.supportContent").value(nullValue()))
                .andExpect(jsonPath("$.result.applyEndDate").value(nullValue()))
                .andExpect(jsonPath("$.result.applyUrl").value(nullValue()))
                .andExpect(jsonPath("$.result.applyPeriodCode").value("ALWAYS"));
    }

    // 1차 FavoriteService.createFavorite는 신청 기간을 검사하지 않습니다.
    @ParameterizedTest
    @ValueSource(strings = {"closed", "expired", "future"})
    void allowsClosedExpiredAndNotYetStartedPolicies(String state) throws Exception {
        var builder = policy(state);
        switch (state) {
            case "closed" -> builder.applyPeriodCode(PolicyApplyPeriod.CLOSED);
            case "expired" -> builder.applyEndDate(LocalDate.of(2000, 1, 1));
            case "future" -> builder.applyStartDate(LocalDate.of(2100, 1, 1));
            default -> throw new IllegalArgumentException(state);
        }
        save(builder);
        mvc.perform(get("/internal/policies/{policyId}", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.policyId").value(state))
                .andExpect(jsonPath("$.result.applyPeriodCode")
                        .value(state.equals("closed") ? "CLOSED" : "SPECIFIC_PERIOD"))
                .andExpect(jsonPath("$.result.activeYn").value(true))
                .andExpect(jsonPath("$.result.deleted").value(false));
    }

    // 1차 관심 등록의 정책 존재·삭제·활성 기준을 실제 Repository와 검증합니다.
    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted", "inactive"})
    void rejectsMissingDeletedAndInactivePoliciesWithSame404(String state) throws Exception {
        if (!state.equals("missing")) {
            Policy policy = policies.save(policy(state).activeYn(!state.equals("inactive")).build());
            if (state.equals("deleted")) {
                policy.softDelete();
            }
            em.flush();
            em.clear();
        }
        mvc.perform(get("/internal/policies/{policyId}", state))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("POLICY_001"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 정책입니다."))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    private Policy.PolicyBuilder policy(String id) {
        return Policy.builder().policyId(id).policyName("청년 지원")
                .categories(Set.of(PolicyCategory.MONTHLY_RENT)).ageLimitYn(false)
                .activeYn(true).applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD);
    }

    private void save(Policy.PolicyBuilder builder) {
        policies.save(builder.build());
        em.flush();
        em.clear();
    }
}
