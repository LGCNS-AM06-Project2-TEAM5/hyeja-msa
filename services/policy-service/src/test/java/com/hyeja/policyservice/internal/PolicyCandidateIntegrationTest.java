package com.hyeja.policyservice.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.*;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.repository.RegionRepository;
import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PolicyCandidateIntegrationTest {
    private static final String PATH = "/internal/policies/candidates";
    // 서버의 현재 날짜 대신 요청 날짜를 사용하는지도 검증합니다.
    private static final LocalDate DATE = LocalDate.of(2030, 1, 2);
    @Autowired private MockMvc mvc;
    @Autowired private PolicyRepository policies;
    @Autowired private PolicyRegionRepository links;
    @Autowired private RegionRepository regions;
    @Autowired private CardNewsRepository cards;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;

    @BeforeEach
    void clearPolicies() {
        cards.deleteAll();
        links.deleteAll();
        policies.deleteAll();
    }

    @Test
    void returnsAgreedJsonWithFullRegionsNullsAndEnumsInTwoQueries() throws Exception {
        Policy regional = policies.save(policy("regional").ageLimitYn(true).minAge(19).maxAge(34)
                .incomeConditionCode(PolicyIncomeCondition.COMPARABLE).incomeMin(100).incomeMax(200)
                .incomeEtc("중위소득 기준").employmentCodes(Set.of(PolicyEmploymentCondition.UNEMPLOYED))
                .houselessRequirement(PolicyHouselessRequirement.REQUIRED)
                .extraQualification("추가 확인").applyStartDate(DATE).applyEndDate(DATE).build());
        link(regional, "11440", "서울특별시 마포구");
        link(regional, "11680", "서울특별시 강남구");
        link(regional, "26110", "삭제 연결").softDelete();
        PolicyRegion deletedRegionLink = link(regional, "26140", "삭제 지역");
        deletedRegionLink.getRegion().softDelete();
        policies.save(policy("nationwide").categories(Set.of(PolicyCategory.JEONSE)).build());
        em.flush();
        em.clear();
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("""
                {"categories":["MONTHLY_RENT","JEONSE"],"regionCode":"11440","today":"2030-01-02"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].policyId").value("regional"))
                .andExpect(jsonPath("$.result[0].policyName").value("regional"))
                .andExpect(jsonPath("$.result[0].categories[0]").value("MONTHLY_RENT"))
                .andExpect(jsonPath("$.result[0].applyPeriodCode").value("SPECIFIC_PERIOD"))
                .andExpect(jsonPath("$.result[0].applyStartDate").value("2030-01-02"))
                .andExpect(jsonPath("$.result[0].applyEndDate").value("2030-01-02"))
                .andExpect(jsonPath("$.result[0].ageLimitYn").value(true))
                .andExpect(jsonPath("$.result[0].minAge").value(19))
                .andExpect(jsonPath("$.result[0].maxAge").value(34))
                .andExpect(jsonPath("$.result[0].incomeConditionCode").value("COMPARABLE"))
                .andExpect(jsonPath("$.result[0].incomeMin").value(100))
                .andExpect(jsonPath("$.result[0].incomeMax").value(200))
                .andExpect(jsonPath("$.result[0].incomeEtc").value("중위소득 기준"))
                .andExpect(jsonPath("$.result[0].employmentCodes[0]").value("UNEMPLOYED"))
                .andExpect(jsonPath("$.result[0].houselessRequirement").value("REQUIRED"))
                .andExpect(jsonPath("$.result[0].extraQualification").value("추가 확인"))
                .andExpect(jsonPath("$.result[0].regions.length()").value(2))
                .andExpect(jsonPath("$.result[0].regions[0].regionCode").value("11440"))
                .andExpect(jsonPath("$.result[0].regions[0].sigunguName").value("서울특별시 마포구"))
                .andExpect(jsonPath("$.result[0].regions[1].regionCode").value("11680"))
                .andExpect(jsonPath("$.result[1].regions").isEmpty())
                .andExpect(jsonPath("$.result[1].employmentCodes").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.result[1].applyEndDate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.result[1].incomeConditionCode").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.result[1].isFavorite").doesNotExist());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void returnsEmptySuccessWhenNoCandidateExists() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("""
                {"categories":[],"regionCode":"11440","today":"2030-01-02"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_001"))
                .andExpect(jsonPath("$.result").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void returnsContractErrorForInvalidRequests(String body, String code) throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("{}", "COMMON_003"),
                Arguments.of("{\"categories\":[],\"regionCode\":\"11440\"}", "COMMON_003"),
                Arguments.of("{\"categories\":[],\"today\":\"2030-01-02\"}", "COMMON_003"),
                Arguments.of("{\"regionCode\":\"11440\",\"today\":\"2030-01-02\"}", "COMMON_003"),
                Arguments.of(request("null", "11440", "2030-01-02"), "COMMON_003"),
                Arguments.of(request("[null]", "11440", "2030-01-02"), "COMMON_003"),
                Arguments.of(request("[]", "", "2030-01-02"), "COMMON_003"),
                Arguments.of(request("[]", "1144", "2030-01-02"), "COMMON_003"),
                Arguments.of(request("[]", "abcde", "2030-01-02"), "COMMON_003"),
                Arguments.of(request("[\"INVALID\"]", "11440", "2030-01-02"), "COMMON_001"),
                Arguments.of(request("[]", "11440", "2030-02-30"), "COMMON_001"),
                Arguments.of(request("[]", "11440", "wrong-date"), "COMMON_001"),
                Arguments.of("{", "COMMON_001"),
                Arguments.of("", "COMMON_001"));
    }

    private static String request(String categories, String region, String date) {
        return "{\"categories\":%s,\"regionCode\":\"%s\",\"today\":\"%s\"}"
                .formatted(categories, region, date);
    }

    private Policy.PolicyBuilder policy(String id) {
        return Policy.builder().policyId(id).policyName(id)
                .categories(Set.of(PolicyCategory.MONTHLY_RENT)).ageLimitYn(false)
                .applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD);
    }

    private PolicyRegion link(Policy policy, String code, String name) {
        Region region = regions.save(Region.builder().regionCode(code).sigunguName(name).build());
        return links.save(PolicyRegion.builder().policy(policy).region(region).build());
    }
}
