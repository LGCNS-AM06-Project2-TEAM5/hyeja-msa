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
import com.hyeja.policyservice.policy.enums.PolicyIncomeCondition;
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
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.transaction.annotation.Transactional;

// 로그인 회원의 정책 상세: Gateway 헤더 → 상세 → user(F6) Feign → 1차 판정까지 실제로 이어지는지 봅니다.
// user-service 자리에 JDK HttpServer를 띄웁니다. 기준 날짜는 2026-09-27(한국 시간)입니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PolicyMemberDetailIntegrationTest.FixedClock.class)
@Transactional
class PolicyMemberDetailIntegrationTest {

    private static final HttpServer USER_SERVICE = startUserService();

    @Autowired private MockMvc mvc;
    @Autowired private PolicyRepository policies;
    @Autowired private PolicyRegionRepository links;
    @Autowired private RegionRepository regions;

    @DynamicPropertySource
    static void userServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.userClient.url",
                () -> "http://localhost:" + USER_SERVICE.getAddress().getPort());
        registry.add("spring.cloud.openfeign.client.config.engagementClient.url",
                () -> "http://localhost:" + USER_SERVICE.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        USER_SERVICE.stop(0);
    }

    @BeforeEach
    void setUp() {
        Policy policy = policies.save(Policy.builder()
                .policyId("member-detail").policyName("청년 월세 지원")
                .categories(Set.of(PolicyCategory.MONTHLY_RENT))
                .ageLimitYn(true).minAge(19).maxAge(34)
                .incomeConditionCode(PolicyIncomeCondition.NO_RESTRICTION)
                .employmentCodes(Set.of(PolicyEmploymentCondition.UNEMPLOYED))
                .houselessRequirement(PolicyHouselessRequirement.REQUIRED)
                .applyPeriodCode(PolicyApplyPeriod.ALWAYS).activeYn(true)
                .build());
        Region mapo = regions.save(Region.builder().regionCode("11440").sigunguName("서울특별시 마포구").build());
        links.save(PolicyRegion.builder().policy(policy).region(mapo).build());
    }

    @Test
    void 회원_조건으로_다섯_가지를_판정한다() throws Exception {
        mvc.perform(get("/api/policies/member-detail").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.overallStatus").value("ABLE"))
                .andExpect(jsonPath("$.result.isFavorite").value(true))
                .andExpect(jsonPath("$.result.conditions.length()").value(5))
                .andExpect(jsonPath("$.result.conditions[0].type").value("AGE"))
                .andExpect(jsonPath("$.result.conditions[0].memberValue").value("만 26세"))
                .andExpect(jsonPath("$.result.conditions[1].type").value("REGION"))
                .andExpect(jsonPath("$.result.conditions[1].status").value("ABLE"))
                .andExpect(jsonPath("$.result.conditions[1].memberValue").value("서울특별시 마포구"));
    }

    @Test
    void 조건을_등록하지_않은_회원은_PROFILE_001() throws Exception {
        mvc.perform(get("/api/policies/member-detail").header("X-Member-Id", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_001"));
    }

    @Test
    void user_서비스가_실패하면_판정_없이_하트만_보여_준다() throws Exception {
        mvc.perform(get("/api/policies/member-detail").header("X-Member-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isFavorite").value(true))
                .andExpect(jsonPath("$.result.policyName").value("청년 월세 지원"))
                .andExpect(jsonPath("$.result.overallStatus").doesNotExist())
                .andExpect(jsonPath("$.result.conditions.length()").value(0));
    }

    private static HttpServer startUserService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/members/1/profile", exchange -> respond(exchange, 200, """
                    {"isSuccess":true,"code":"SUCCESS_001","message":"성공입니다.","result":{
                      "memberId":1,"birth":"2000-03-15",
                      "region":{"regionCode":"11440","sigunguName":"서울특별시 마포구"},
                      "employmentCode":"UNEMPLOYED","houselessYn":true,"marriageCode":null,
                      "incomeRangeCode":"R2000_3000","educationCode":null,"housingType":null}}
                    """));
            server.createContext("/internal/members/2/profile", exchange -> respond(exchange, 404, """
                    {"isSuccess":false,"code":"PROFILE_001","message":"등록된 조건이 없습니다.","result":null}
                    """));
            server.createContext("/internal/members/3/profile", exchange -> respond(exchange, 500, """
                    {"isSuccess":false,"code":"SERVER_001","message":"서버 오류가 발생했습니다.","result":null}
                    """));
            // engagement F4 대신: 요청 본문의 회원·정책 ID를 보고 관심 등록한 정책만 돌려줍니다.
            server.createContext("/internal/favorites/active-policy-ids", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String result = !body.contains("\"memberId\":2") && body.contains("member-detail") ? "[\"member-detail\"]" : "[]";
                respond(exchange, 200, "{\"isSuccess\":true,\"code\":\"SUCCESS_001\",\"message\":\"성공입니다.\",\"result\":" + result + "}");
            });
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
        Clock memberDetailTestClock() {
            return Clock.fixed(Instant.parse("2026-09-26T16:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
