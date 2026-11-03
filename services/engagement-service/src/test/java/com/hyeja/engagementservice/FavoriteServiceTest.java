
package com.hyeja.engagementservice;

import com.hyeja.engagementservice.favorite.service.FavoriteService;
import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.policy.enums.*;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import com.hyeja.engagementservice.global.exception.GeneralException;

import java.time.LocalDate;
import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FavoriteServiceTest {

    @Autowired
    FavoriteService favorites;

    @Autowired
    PolicyRepository policies;

    @Autowired
    org.springframework.web.context.WebApplicationContext context;

    final LocalDate today = LocalDate.of(2026, 11, 2);

    @BeforeEach
    void setup() {
        Policy policy = new Policy();
        policy.setPolicyId("P1");
        policy.setPolicyName("월세 100% 지원");
        policy.setCategories(Set.of(PolicyCategory.MONTHLY_RENT));
        policy.setSupportContent("지원 내용");
        policy.setApplyEndDate(today.plusDays(7));
        policy.setApplyPeriodCode(PolicyApplyPeriod.SPECIFIC_PERIOD);
        policy.setActiveYn(true);
        policies.saveAndFlush(policy);
    }

    // 기존 테스트 1: 관심 등록 / 검색 / 삭제
    @Test
    void favoriteContractAndLiteralSearch() {
        var item = favorites.createFavorite(1L, "P1");

        assertThat(item.getPolicyId()).isEqualTo("P1");
        assertThat(item.getCreatedAt()).isNotNull();

        assertThatThrownBy(() -> favorites.createFavorite(1L, "P1"))
                .isInstanceOf(GeneralException.class);

        assertThat(favorites.getMyFavorites(1L, "100%", 0, 8)
                .getTotalElements()).isEqualTo(1);

        assertThat(favorites.getMyFavorites(2L, null, 0, 8)
                .getTotalElements()).isZero();

        favorites.deleteFavorite(1L, "P1");

        assertThat(favorites.getMyFavorites(1L, null, 0, 8)
                .getTotalElements()).isZero();

        assertThat(favorites.createFavorite(1L, "P1").getFavoriteId())
                .isNotEqualTo(item.getFavoriteId());
    }

    // 기존 테스트 2: HTTP 및 인증
    @Test
    void httpContractAndAuthentication() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup
                        .SecurityMockMvcConfigurers.springSecurity())
                .build();

        mvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/favorite"))
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.status().isUnauthorized());

        mvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.post("/api/favorite/P1")
                        .header(
                                "X-Gateway-Assertion",
                                com.hyeja.engagementservice.security
                                        .InternalTokenFixture.valid(1L)
                        ))
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.jsonPath("$.code")
                        .value("SUCCESS_001"))
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.jsonPath("$.result.policy_id")
                        .value("P1"));

        mvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/favorite")
                        .header(
                                "X-Gateway-Assertion",
                                com.hyeja.engagementservice.security
                                        .InternalTokenFixture.valid(1L)
                        )
                        .param("page", "-1"))
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.status().isBadRequest());
    }

    // F3 테스트 1: 등록된 정책만 반환
    @Test
    void findActivePolicyIdsReturnsOnlyFavorites() {
        favorites.createFavorite(1L, "P1");

        List<String> result = favorites.findActivePolicyIds(
                1L,
                List.of("P1", "P2", "P3")
        );

        assertThat(result).containsExactly("P1");
    }

    // F3 테스트 2: 관심 등록이 없다면 빈 목록 반환
    @Test
    void findActivePolicyIdsReturnsEmptyWhenNoFavorites() {
        List<String> result = favorites.findActivePolicyIds(
                2L,
                List.of("P1")
        );

        assertThat(result).isEmpty();
    }

    // F3 테스트 3: 빈 요청 및 중복 ID 처리
    @Test
    void findActivePolicyIdsHandlesEmptyAndDuplicateIds() {
        assertThat(favorites.findActivePolicyIds(1L, List.of()))
                .isEmpty();

        favorites.createFavorite(1L, "P1");

        List<String> result = favorites.findActivePolicyIds(
                1L,
                List.of("P1", "P1", "P2")
        );

        assertThat(result).containsExactly("P1");
    }
}
