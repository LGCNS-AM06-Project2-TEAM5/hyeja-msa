package com.hyeja.recommendationservice.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hyeja.recommendationservice.favorite.client.FavoriteClientAdapter;
import com.hyeja.recommendationservice.favorite.client.FavoriteFeignClient;
import com.hyeja.recommendationservice.global.apiPayload.ApiResponse;
import com.hyeja.recommendationservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.recommendationservice.global.config.CircuitBreakerIgnorePredicate;
import com.hyeja.recommendationservice.global.exception.ExceptionAdvice;
import com.hyeja.recommendationservice.global.exception.GeneralException;
import com.hyeja.recommendationservice.policy.client.PolicyCandidateFeignClient;
import com.hyeja.recommendationservice.policy.client.PolicyClientAdapter;
import com.hyeja.recommendationservice.policy.controller.PolicySearchController;
import com.hyeja.recommendationservice.policy.dto.PolicyCandidateDTO;
import com.hyeja.recommendationservice.policy.dto.PolicySearchResponseDTO;
import com.hyeja.recommendationservice.policy.enums.PolicyApplyPeriod;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import com.hyeja.recommendationservice.profile.client.ProfileClientAdapter;
import com.hyeja.recommendationservice.profile.client.UserProfileFeignClient;
import com.hyeja.recommendationservice.profile.dto.MemberProfileDTO;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// 회로가 열렸을 때 검색 API가 기존 계약대로 응답하는지 확인합니다.
// F1·F2는 빈 데이터로 바꾸지 않고 검색 실패(500 SERVER_001), F3는 관심 표시만 빼고(isFavorite=false) 검색을 이어갑니다.
class PolicySearchCircuitBreakerTest {

    private final UserProfileFeignClient userFeign = mock(UserProfileFeignClient.class);
    private final PolicyCandidateFeignClient policyFeign = mock(PolicyCandidateFeignClient.class);
    private final FavoriteFeignClient favoriteFeign = mock(FavoriteFeignClient.class);
    private final PolicyEligibilityEvaluator evaluator = mock(PolicyEligibilityEvaluator.class);
    private final PolicySearchAiAnalyzer aiAnalyzer = mock(PolicySearchAiAnalyzer.class);
    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .ignoreException(new CircuitBreakerIgnorePredicate()).build());
    private final PolicySearchService service = new PolicySearchService(
            new PolicyClientAdapter(policyFeign, registry),
            new FavoriteClientAdapter(favoriteFeign, registry),
            new ProfileClientAdapter(userFeign, registry),
            evaluator, aiAnalyzer,
            Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul")));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PolicySearchController(service))
            .setControllerAdvice(new ExceptionAdvice()).build();

    @Test
    void 관심_회로가_열려도_검색은_이어가고_관심_표시만_뺀다() {
        givenProfileAndCandidates();
        registry.circuitBreaker("recEngagementFavorites").transitionToOpenState();

        PolicySearchResponseDTO response = service.search(1L, "#월세");

        assertThat(response.approved()).singleElement().satisfies(item -> {
            assertThat(item.policyId()).isEqualTo("P-1");
            assertThat(item.isFavorite()).isFalse();
        });
        verify(favoriteFeign, never()).findActivePolicyIds(any());
    }

    @Test
    void 회원_조건_회로가_열리면_빈_조건으로_검색하지_않고_SERVER_001로_실패한다() throws Exception {
        givenProfileAndCandidates();
        registry.circuitBreaker("recUserProfile").transitionToOpenState();

        assertThatThrownBy(() -> service.search(1L, "#월세")).isInstanceOf(CallNotPermittedException.class);
        mvc.perform(get("/api/policies/search").header("X-Member-Id", "1").param("query", "#월세"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SERVER_001"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
        verify(userFeign, never()).getMemberProfile(any());
        verify(policyFeign, never()).findCandidates(any());
    }

    @Test
    void 정책_후보_회로가_열리면_빈_검색_결과로_바꾸지_않고_SERVER_001로_실패한다() throws Exception {
        givenProfileAndCandidates();
        registry.circuitBreaker("recPolicyCandidates").transitionToOpenState();

        assertThatThrownBy(() -> service.search(1L, "#월세")).isInstanceOf(CallNotPermittedException.class);
        mvc.perform(get("/api/policies/search").header("X-Member-Id", "1").param("query", "#월세"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SERVER_001"));
        verify(policyFeign, never()).findCandidates(any());
    }

    @Test
    void 회로가_닫혀_있으면_MEMBER_001과_PROFILE_001은_그대로_404로_응답한다() throws Exception {
        given(userFeign.getMemberProfile(1L))
                .willThrow(new GeneralException(ErrorStatus.MEMBER_NOT_FOUND))
                .willThrow(new GeneralException(ErrorStatus.PROFILE_NOT_FOUND));

        mvc.perform(get("/api/policies/search").header("X-Member-Id", "1").param("query", "#월세"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_001"));
        mvc.perform(get("/api/policies/search").header("X-Member-Id", "1").param("query", "#월세"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_001"));
        assertThat(registry.circuitBreaker("recUserProfile").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    private void givenProfileAndCandidates() {
        given(userFeign.getMemberProfile(1L)).willReturn(ApiResponse.onSuccess(new MemberProfileDTO(
                1L, LocalDate.of(2000, 3, 15), new MemberProfileDTO.Region("11440", "서울특별시 마포구"),
                null, true, null, null, null, null)));
        given(policyFeign.findCandidates(any())).willReturn(ApiResponse.onSuccess(List.of(new PolicyCandidateDTO(
                "P-1", "청년 월세 지원", List.of(PolicyCategory.MONTHLY_RENT), PolicyApplyPeriod.ALWAYS,
                null, null, false, null, null, null, null, null, null, null, null, null, List.of()))));
        given(favoriteFeign.findActivePolicyIds(any())).willReturn(ApiResponse.onSuccess(List.of("P-1")));
    }
}
