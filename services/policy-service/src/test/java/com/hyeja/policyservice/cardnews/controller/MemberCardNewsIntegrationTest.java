package com.hyeja.policyservice.cardnews.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.cardnews.entity.CardNews;
import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.entity.PolicyRegion;
import com.hyeja.policyservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.policyservice.policy.enums.PolicyCategory;
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

// 로그인 회원 홈 카드뉴스: 헤더 → user(F6) Feign → 회원 지역 쿼리까지 실제로 이어지는지 봅니다.
// 회원은 마포구(11440). 마포구 정책·서울 전체(11000) 정책·전국 정책은 나오고, 부산 정책은 빠져야 합니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MemberCardNewsIntegrationTest.FixedClock.class)
@Transactional
class MemberCardNewsIntegrationTest {

    private static final HttpServer USER_SERVICE = startUserService();

    @Autowired private MockMvc mvc;
    @Autowired private PolicyRepository policies;
    @Autowired private PolicyRegionRepository links;
    @Autowired private RegionRepository regions;
    @Autowired private CardNewsRepository cards;

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
        Region mapo = regions.save(Region.builder().regionCode("11440").sigunguName("서울특별시 마포구").build());
        Region seoul = regions.save(Region.builder().regionCode("11000").sigunguName("서울특별시").build());
        Region busan = regions.save(Region.builder().regionCode("26110").sigunguName("부산광역시 중구").build());
        withCard(policy("card-mapo", LocalDate.of(2026, 10, 1)), mapo);
        withCard(policy("card-seoul", LocalDate.of(2026, 10, 2)), seoul);
        withCard(policy("card-busan", LocalDate.of(2026, 10, 3)), busan);
        withCard(policy("card-nation", LocalDate.of(2026, 10, 4)), null);
    }

    @Test
    void 회원_지역에_맞는_카드만_마감순으로_보여_준다() throws Exception {
        mvc.perform(get("/api/policies/card-news").header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(3))
                .andExpect(jsonPath("$.result[0].policyId").value("card-mapo"))
                .andExpect(jsonPath("$.result[1].policyId").value("card-seoul"))
                .andExpect(jsonPath("$.result[2].policyId").value("card-nation"))
                .andExpect(jsonPath("$.result[0].applyEndDate").value("2026-10-01"));
    }

    @Test
    void 회원_헤더가_없으면_401() throws Exception {
        mvc.perform(get("/api/policies/card-news"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @Test
    void user_서비스가_실패하면_비로그인_홈_카드뉴스로_대신한다() throws Exception {
        mvc.perform(get("/api/policies/card-news").header("X-Member-Id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(4))
                .andExpect(jsonPath("$.result[2].policyId").value("card-busan"));
    }

    private Policy policy(String id, LocalDate applyEndDate) {
        return policies.save(Policy.builder().policyId(id).policyName(id).description(id + " 설명")
                .categories(Set.of(PolicyCategory.MONTHLY_RENT)).ageLimitYn(false)
                .applyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD)
                .applyStartDate(LocalDate.of(2026, 9, 1)).applyEndDate(applyEndDate)
                .activeYn(true).viewCount(0).build());
    }

    private void withCard(Policy policy, Region region) {
        if (region != null) {
            links.save(PolicyRegion.builder().policy(policy).region(region).build());
        }
        cards.save(CardNews.builder().policy(policy).cardNo(1L).title(policy.getPolicyName()).body("본문").build());
    }

    private static HttpServer startUserService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/members/1/profile", exchange -> respond(exchange, 200, """
                    {"isSuccess":true,"code":"SUCCESS_001","message":"성공입니다.","result":{
                      "memberId":1,"birth":"2000-03-15",
                      "region":{"regionCode":"11440","sigunguName":"서울특별시 마포구"},
                      "employmentCode":"UNEMPLOYED","houselessYn":true,"incomeRangeCode":null}}
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
        Clock memberCardNewsTestClock() {
            return Clock.fixed(Instant.parse("2026-09-27T16:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
