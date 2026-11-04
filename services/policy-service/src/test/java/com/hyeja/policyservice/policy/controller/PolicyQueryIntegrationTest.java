package com.hyeja.policyservice.policy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.repository.RegionRepository;
import com.hyeja.policyservice.term.entity.Term;
import com.hyeja.policyservice.term.repository.TermRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PolicyQueryIntegrationTest.FixedClock.class)
@Transactional
class PolicyQueryIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private PolicyRepository policies;
    @Autowired private PolicyRegionRepository links;
    @Autowired private RegionRepository regions;
    @Autowired private TermRepository terms;
    @Autowired private CardNewsRepository cards;

    @BeforeEach
    void setUp() {
        cards.deleteAll();
        links.deleteAll();
        policies.deleteAll();
        terms.deleteAll();
        Policy regional = policies.save(policy("query-regional", "월세 정책", PolicyApplyPeriod.SPECIFIC_PERIOD)
                .applyEndDate(LocalDate.of(2026, 9, 30)).extraQualification("무주택자 대상").build());
        Region region = regions.save(Region.builder().regionCode("11440").sigunguName("서울특별시 마포구").build());
        links.save(PolicyRegion.builder().policy(regional).region(region).build());
        policies.save(policy("query-always", "상시 월세", PolicyApplyPeriod.ALWAYS).build());
        policies.save(policy("query-closed", "마감 월세", PolicyApplyPeriod.CLOSED).build());
        terms.save(Term.builder().term("무주택자").easyDescription("주택이 없는 사람").build());
    }

    @Test
    void servesFilteredSortedPageFromDatabaseUsingKoreanDate() throws Exception {
        // UTC는 9/26이지만 한국 기준 9/27입니다. 마감일까지 3일입니다.
        mvc.perform(get("/api/policies/housing").param("category", "MONTHLY_RENT").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.policies[0].policy_id").value("query-regional"))
                .andExpect(jsonPath("$.result.policies[0].region_summary").value("서울특별시 마포구"))
                .andExpect(jsonPath("$.result.policies[0].apply_end_date").value("2026-09-30"))
                .andExpect(jsonPath("$.result.policies[0].d_day").value(3));
        mvc.perform(get("/api/policies/housing").param("category", "MONTHLY_RENT")
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.policies[0].policy_id").value("query-always"))
                .andExpect(jsonPath("$.result.policies[0].nationwide").value(true))
                .andExpect(jsonPath("$.result.policies[0].region_summary").value("전국"))
                .andExpect(jsonPath("$.result.policies[0].d_day").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void servesGuestDetailAndTermsWithoutMemberCalls() throws Exception {
        mvc.perform(get("/api/policies/query-regional"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.policyName").value("월세 정책"))
                .andExpect(jsonPath("$.result.applyEndDate").value("2026-09-30"))
                .andExpect(jsonPath("$.result.terms[0].termName").value("무주택자"))
                .andExpect(jsonPath("$.result.isFavorite").value(false))
                .andExpect(jsonPath("$.result.overallStatus").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.result.conditions").isEmpty());
        mvc.perform(get("/api/policies/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_001"));
    }

    private Policy.PolicyBuilder policy(String id, String name, PolicyApplyPeriod period) {
        return Policy.builder().policyId(id).policyName(name).categories(Set.of(PolicyCategory.MONTHLY_RENT))
                .ageLimitYn(false).applyPeriodCode(period).activeYn(true);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock queryTestClock() {
            return Clock.fixed(Instant.parse("2026-09-26T16:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
