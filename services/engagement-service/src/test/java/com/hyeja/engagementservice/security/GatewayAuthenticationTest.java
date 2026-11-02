package com.hyeja.engagementservice.security;

import com.hyeja.engagementservice.notification.service.NotificationGenerationService;
import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.policy.enums.*;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static com.hyeja.engagementservice.security.InternalTokenFixture.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest @ActiveProfiles("test") @Transactional
class GatewayAuthenticationTest {
    @Autowired WebApplicationContext context;
    @Autowired PolicyRepository policies;
    @Autowired NotificationGenerationService generation;
    MockMvc mvc;

    @BeforeEach void setup() { mvc = webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void directAccessAndInvalidAssertionsAreRejected() throws Exception {
        mvc.perform(get("/api/favorite").header("X-Member-Id", "2"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMON_002"));
        mvc.perform(get("/api/notification").header("Authorization", "Bearer " + valid(1)))
                .andExpect(status().isUnauthorized());
        List<String> invalid = List.of("broken", "", sign(claims(1), "different-test-only-key-at-least-32-bytes"),
                sign(claims(1).issuer("user-service")),
                sign(claims(1).issuer(null)),
                sign(claims(1).audience().clear().add("policy-service").and()),
                sign(claims(1).audience().clear().and()),
                sign(claims(1).expiration(Date.from(Instant.now().minusSeconds(1)))),
                sign(claims(1).issuedAt(null)), sign(claims(1).expiration(null)),
                sign(claims(1).subject("not-a-number")), valid(0),
                sign(claims(1).issuedAt(Date.from(Instant.now().plusSeconds(60)))),
                sign(claims(1).expiration(Date.from(Instant.now().plusSeconds(600)))));
        for (String assertion : invalid) {
            mvc.perform(get("/api/favorite").header("X-Gateway-Assertion", assertion))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.result").doesNotExist());
        }
        mvc.perform(get("/api/favorite").header("X-Gateway-Assertion", valid(1), valid(2)))
                .andExpect(status().isUnauthorized());
        String differentAlgorithm = claims(1).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-only-algorithm-key-with-at-least-sixty-four-bytes-for-hs512-1234"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), io.jsonwebtoken.Jwts.SIG.HS512).compact();
        mvc.perform(get("/api/favorite").header("X-Gateway-Assertion", differentAlgorithm))
                .andExpect(status().isUnauthorized());
        // Request threads must not retain the preceding request's principal.
        mvc.perform(get("/api/favorite").header("X-Gateway-Assertion", valid(1))).andExpect(status().isOk());
        mvc.perform(get("/api/favorite")).andExpect(status().isUnauthorized());
    }

    @Test void signedMemberOwnsFavoritesAndNotificationsRegardlessOfIdentityHeaders() throws Exception {
        Policy p = new Policy();
        p.setPolicyId("AUTH-POLICY"); p.setPolicyName("인증 테스트");
        p.setCategories(Set.of(PolicyCategory.MONTHLY_RENT)); p.setActiveYn(true);
        p.setApplyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD);
        LocalDate today = LocalDate.of(2026, 11, 2);
        p.setApplyEndDate(today.plusDays(7)); policies.saveAndFlush(p);
        mvc.perform(post("/api/favorite/AUTH-POLICY").header("X-Gateway-Assertion", valid(1))
                        .header("X-Member-Id", "2")).andExpect(status().isOk());
        mvc.perform(get("/api/favorite").header("X-Gateway-Assertion", valid(1)).header("X-Member-Id", "2"))
                .andExpect(jsonPath("$.result.totalElements").value(1));
        mvc.perform(get("/api/favorite").header("X-Gateway-Assertion", valid(2)).header("X-Member-Id", "1"))
                .andExpect(jsonPath("$.result.totalElements").value(0));
        generation.createDeadlineNotifications(today);
        mvc.perform(get("/api/notification").header("X-Gateway-Assertion", valid(1)))
                .andExpect(jsonPath("$.result.notifications[0].member_id").value(1));
        // Repository ID lookup avoids relying on a particular test database sequence.
        Long id = context.getBean(com.hyeja.engagementservice.notification.repository.NotificationRepository.class)
                .findAllByMemberId(1L, org.springframework.data.domain.PageRequest.of(0, 8))
                .getContent().get(0).getNotificationId();
        mvc.perform(patch("/api/notification/" + id + "/read").header("X-Gateway-Assertion", valid(2))
                        .header("X-Member-Id", "1")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/notification/" + id).header("X-Gateway-Assertion", valid(2))
                        .header("X-Member-Id", "1")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/notification/" + id + "/read").header("X-Gateway-Assertion", valid(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.read_yn").value(true));
        mvc.perform(delete("/api/notification/" + id).header("X-Gateway-Assertion", valid(1)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/favorite/AUTH-POLICY").header("X-Gateway-Assertion", valid(2))
                        .header("X-Member-Id", "1")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/favorite/AUTH-POLICY").header("X-Gateway-Assertion", valid(1)))
                .andExpect(status().isOk());
    }

    @Test void healthChecksRemainPublicButOtherActuatorEndpointsDoNot() throws Exception {
        mvc.perform(get("/health-check")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
