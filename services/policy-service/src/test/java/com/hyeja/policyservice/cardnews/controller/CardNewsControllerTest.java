package com.hyeja.policyservice.cardnews.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.policyservice.cardnews.dto.CardNewsDetailResponseDTO;
import com.hyeja.policyservice.cardnews.dto.CardNewsDetailResponseDTO.CardDTO;
import com.hyeja.policyservice.cardnews.service.CardNewsService;
import com.hyeja.policyservice.client.engagement.FavoriteReader;
import com.hyeja.policyservice.global.auth.LoginMemberIdArgumentResolver;
import com.hyeja.policyservice.global.auth.MemberHeaders;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// 1차는 SecurityContext의 회원 ID를 썼고, 지금은 Gateway가 넘겨준 X-Member-Id 헤더를 씁니다.
class CardNewsControllerTest {
    private final CardNewsService cardNewsService = mock(CardNewsService.class);
    private final FavoriteReader favoriteReader = mock(FavoriteReader.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CardNewsController(cardNewsService, favoriteReader))
                .setCustomArgumentResolvers(new LoginMemberIdArgumentResolver())
                .build();
    }

    @Test
    void returnsCardNewsPopupDetail() throws Exception {
        when(cardNewsService.getCardNewsDetail("20260923005400113576", 1L)).thenReturn(
                new CardNewsDetailResponseDTO(
                        "policy-1", "월세", 5, true, false,
                        "https://example.com/apply",
                        List.of(new CardDTO(10L, 2L, null,
                                List.of("만 19~34세", "전국"), "신청 대상"))));
        when(favoriteReader.isFavorite(1L, "20260923005400113576")).thenReturn(true);

        mvc.perform(get("/api/policies/card-detail/20260923005400113576")
                        .header(MemberHeaders.MEMBER_ID, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.policyId").value("policy-1"))
                .andExpect(jsonPath("$.result.categoryLabel").value("월세"))
                .andExpect(jsonPath("$.result.isAuthenticated").value(true))
                .andExpect(jsonPath("$.result.isFavorite").value(true))
                .andExpect(jsonPath("$.result.cards[0].badges[0]").value("만 19~34세"));

        verify(cardNewsService).getCardNewsDetail("20260923005400113576", 1L);
    }

    // 헤더가 없으면 비로그인으로 보고 회원 ID 없이 조회합니다.
    @Test
    void treatsMissingMemberHeaderAsGuest() throws Exception {
        when(cardNewsService.getCardNewsDetail("policy-1", null)).thenReturn(
                new CardNewsDetailResponseDTO("policy-1", "월세", 5, false, false,
                        "https://example.com/apply", List.of()));

        mvc.perform(get("/api/policies/card-detail/policy-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isAuthenticated").value(false));

        verify(cardNewsService).getCardNewsDetail("policy-1", null);
        verifyNoInteractions(favoriteReader);
    }
}
