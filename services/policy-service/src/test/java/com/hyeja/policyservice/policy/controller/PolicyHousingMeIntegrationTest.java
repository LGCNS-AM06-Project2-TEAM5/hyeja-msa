package com.hyeja.policyservice.policy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
import com.hyeja.policyservice.policy.enums.PolicyEmploymentCondition;
import com.hyeja.policyservice.policy.enums.PolicyHouselessRequirement;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.region.entity.Region;
import com.hyeja.policyservice.region.repository.RegionRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// 로그인 회원 정책 목록(/housing/me): 헤더 → user(F6) Feign → 1차 회원 쿼리까지 실제로 이어지는지 봅니다.
// 목록은 user를 기다리는 동안 트랜잭션 밖에서 조회하므로, 테스트 데이터는 커밋하고 끝나면 직접 지웁니다.
// 회원: 2000-03-15생(기준일 2026-09-27에 만 26세), 마포구(11440), 취업, 무주택.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PolicyHousingMeIntegrationTest.FixedClock.class)
class PolicyHousingMeIntegrationTest {

    private static final HttpServer USER_SERVICE = startUserService();

    @Autowired private MockMvc mvc;
    @Autowired private PolicyRepository policies;
    @Autowired private PolicyRegionRepository links;
    @Autowired private RegionRepository regions;

    private final List<PolicyRegion> savedLinks = new ArrayList<>();
    private final List<Policy> savedPolicies = new ArrayList<>();
    private final List<Region> savedRegions = new ArrayList<>();

    @DynamicPropertySource
    static void userServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.userClient.url",
                () -> "http://localhost:" + USER_SERVICE.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        USER_SERVICE.stop(0);
    }

    @BeforeEach
    void setUp() {
        Region mapo = region("11440", "서울특별시 마포구");
        Region gangnam = region("11680", "서울특별시 강남구");
        policy("me-mapo", LocalDate.of(2026, 10, 1), 19, 39, mapo);
        policy("me-nation", LocalDate.of(2026, 10, 2), 19, 39, null);
        policy("me-gangnam", LocalDate.of(2026, 10, 3), 19, 39, gangnam);
        policy("me-old", LocalDate.of(2026, 10, 4), 30, 39, null);
    }

    @AfterEach
    void cleanUp() {
        links.deleteAll(savedLinks);
        policies.deleteAll(savedPolicies);
        regions.deleteAll(savedRegions);
    }

    @Test
    void 조건에_맞는_정책만_거르면_지역과_나이가_안_맞는_정책이_빠진다() throws Exception {
        mvc.perform(get("/api/policies/housing/me").header("X-Member-Id", "1").param("onlyEligible", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.policies[0].policy_id").value("me-mapo"))
                .andExpect(jsonPath("$.result.policies[0].regions[0].region_code").value("11440"))
                .andExpect(jsonPath("$.result.policies[0].favorite_yn").value(false))
                .andExpect(jsonPath("$.result.policies[0].d_day").value(4))
                .andExpect(jsonPath("$.result.policies[1].policy_id").value("me-nation"))
                .andExpect(jsonPath("$.result.policies[1].nationwide").value(true));
    }

    @Test
    void 거르지_않으면_진행_중인_정책을_모두_보여_준다() throws Exception {
        mvc.perform(get("/api/policies/housing/me").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(4))
                .andExpect(jsonPath("$.result.size").value(8));
    }

    @Test
    void user_서비스가_실패하면_비로그인_목록으로_대신한다() throws Exception {
        mvc.perform(get("/api/policies/housing/me").header("X-Member-Id", "3").param("onlyEligible", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(4))
                .andExpect(jsonPath("$.result.policies[0].favorite_yn").value(false));
    }

    @Test
    void 조건이_없는_회원은_PROFILE_001() throws Exception {
        mvc.perform(get("/api/policies/housing/me").header("X-Member-Id", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_001"));
    }

    @Test
    void 회원_헤더가_없으면_401() throws Exception {
        mvc.perform(get("/api/policies/housing/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void 정렬값이_잘못되면_400() throws Exception {
        mvc.perform(get("/api/policies/housing/me").header("X-Member-Id", "1").param("sort", "POPULAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void 페이지_크기가_50을_넘으면_400() throws Exception {
        mvc.perform(get("/api/policies/housing/me").header("X-Member-Id", "1").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    private Region region(String code, String name) {
        Region saved = regions.save(Region.builder().regionCode(code).sigunguName(name).build());
        savedRegions.add(saved);
        return saved;
    }

    private void policy(String id, LocalDate applyEndDate, int minAge, int maxAge, Region region) {
        Policy policy = policies.save(Policy.builder().policyId(id).policyName(id)
                .categories(Set.of(PolicyCategory.MONTHLY_RENT))
                .ageLimitYn(true).minAge(minAge).maxAge(maxAge)
                .employmentCodes(Set.of(PolicyEmploymentCondition.EMPLOYED))
                .houselessRequirement(PolicyHouselessRequirement.REQUIRED)
                .applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD)
                .applyStartDate(LocalDate.of(2026, 9, 1)).applyEndDate(applyEndDate)
                .activeYn(true).viewCount(0).build());
        savedPolicies.add(policy);
        if (region != null) {
            savedLinks.add(links.save(PolicyRegion.builder().policy(policy).region(region).build()));
        }
    }

    private static HttpServer startUserService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/members/1/profile", exchange -> respond(exchange, 200, """
                    {"isSuccess":true,"code":"SUCCESS_001","message":"성공입니다.","result":{
                      "memberId":1,"birth":"2000-03-15",
                      "region":{"regionCode":"11440","sigunguName":"서울특별시 마포구"},
                      "employmentCode":"EMPLOYED","houselessYn":true,"incomeRangeCode":null}}
                    """));
            server.createContext("/internal/members/2/profile", exchange -> respond(exchange, 404, """
                    {"isSuccess":false,"code":"PROFILE_001","message":"등록된 조건이 없습니다.","result":null}
                    """));
            server.createContext("/internal/members/3/profile", exchange -> respond(exchange, 500, """
                    {"isSuccess":false,"code":"SERVER_001","message":"서버 오류가 발생했습니다.","result":null}
                    """));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock housingMeTestClock() {
            return Clock.fixed(Instant.parse("2026-09-26T16:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
