package com.hyeja.policyservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hyeja.policyservice.cardnews.repository.CardNewsRepository;
import com.hyeja.policyservice.cardnews.service.CardNewsService;
import com.hyeja.policyservice.client.engagement.EngagementClient;
import com.hyeja.policyservice.client.engagement.FavoriteReader;
import com.hyeja.policyservice.client.engagement.dto.EngagementApiResponse;
import com.hyeja.policyservice.client.user.MemberProfileReader;
import com.hyeja.policyservice.client.user.UserClient;
import com.hyeja.policyservice.client.user.dto.MemberProfileDTO;
import com.hyeja.policyservice.client.user.dto.UserApiResponse;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.policy.dto.PolicyMemberDetailDTO;
import com.hyeja.policyservice.policy.entity.Policy;
import com.hyeja.policyservice.policy.enums.PolicySort;
import com.hyeja.policyservice.policy.repository.PolicyRegionRepository;
import com.hyeja.policyservice.policy.repository.PolicyRepository;
import com.hyeja.policyservice.policy.service.PolicyEligibilityEvaluator;
import com.hyeja.policyservice.policy.service.PolicyMemberService;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

// 회로가 열렸을 때 F4·F6를 쓰는 API가 기존 장애 처리와 같게 응답하는지 실제 Reader·Service로 확인합니다.
// F6: 상세는 판정 없이, /housing/me는 비로그인 목록, 회원 카드뉴스는 비로그인 카드로 대신하고 MEMBER_001·PROFILE_001은 그대로 던집니다.
// F4: 회로가 열리면(청크 중간 포함) 하트 없이 응답합니다.
class CircuitBreakerFallbackTest {

    // 한국 2026-11-03
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final UserClient userClient = mock(UserClient.class);
    private final EngagementClient engagementClient = mock(EngagementClient.class);
    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyRegionRepository policyRegionRepository = mock(PolicyRegionRepository.class);
    private final CardNewsRepository cardNewsRepository = mock(CardNewsRepository.class);
    private final PolicyEligibilityEvaluator evaluator = mock(PolicyEligibilityEvaluator.class);
    // 운영 설정(application.yml)과 같고 OPEN 대기 시간만 짧게 둡니다.
    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(100))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(false)
            .ignoreException(new CircuitBreakerIgnorePredicate())
            .build());
    private final MemberProfileReader memberProfileReader = new MemberProfileReader(userClient, registry);
    private final FavoriteReader favoriteReader = new FavoriteReader(engagementClient, registry);
    private final PolicyMemberService policyMemberService = new PolicyMemberService(
            memberProfileReader, favoriteReader, policyRepository, policyRegionRepository, evaluator, CLOCK);
    private final CardNewsService cardNewsService = new CardNewsService(
            cardNewsRepository, policyRegionRepository, memberProfileReader, CLOCK);
    private final Policy policy = policy("P1");

    @Test
    void user_회로가_열리면_상세는_부르지_않고_판정_없이_하트만_채운다() {
        userBreaker().transitionToOpenState();
        given(engagementClient.findActivePolicyIds(any())).willReturn(ok(List.of("P1")));

        PolicyMemberDetailDTO detail = policyMemberService.getMemberDetail(policy, 7L);

        assertThat(detail).isEqualTo(new PolicyMemberDetailDTO(true, null, List.of()));
        verifyNoInteractions(userClient, evaluator);
    }

    @Test
    void user_회로가_열리면_housing_me는_비로그인_목록으로_대신한다() {
        userBreaker().transitionToOpenState();
        PageRequest pageRequest = PageRequest.of(0, 8, PolicySort.DEADLINE.toSort());
        given(policyRepository.findGuestHousingPolicies(null, LocalDate.of(2026, 11, 3), pageRequest))
                .willReturn(new PageImpl<>(List.of(), pageRequest, 0));

        assertThat(policyMemberService.getHousingPoliciesForMember(7L, null, PolicySort.DEADLINE, true, 0, 8)
                .getPolicies()).isEmpty();

        verify(policyRepository).findGuestHousingPolicies(null, LocalDate.of(2026, 11, 3), pageRequest);
        verify(policyRepository, never()).findHousingPoliciesForMember(any(), anyBoolean(), any(), anyInt(),
                anyBoolean(), any(), any(), any());
        verifyNoInteractions(userClient);
    }

    @Test
    void user_회로가_열리면_회원_카드뉴스는_비로그인_카드로_대신한다() {
        userBreaker().transitionToOpenState();
        given(cardNewsRepository.findGuestHomeCardNews(LocalDate.of(2026, 11, 3), PageRequest.of(0, 4)))
                .willReturn(List.of());

        assertThat(cardNewsService.getMemberCardNews(7L)).isEmpty();

        verify(cardNewsRepository).findGuestHomeCardNews(LocalDate.of(2026, 11, 3), PageRequest.of(0, 4));
        verify(cardNewsRepository, never()).findMemberHomeCardNews(any(), any(), any(), any());
        verifyNoInteractions(userClient);
    }

    @Test
    void MEMBER_001과_PROFILE_001은_대체하지_않고_그대로_던지며_회로를_열지_않는다() {
        given(userClient.getMemberProfile(7L)).willThrow(userError(404, "MEMBER_001"));
        for (int i = 0; i < 15; i++) {
            assertThatThrownBy(() -> policyMemberService.getMemberDetail(policy, 7L))
                    .isInstanceOfSatisfying(GeneralException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorStatus.MEMBER_NOT_FOUND));
        }
        willThrow(userError(404, "PROFILE_001")).given(userClient).getMemberProfile(7L);
        assertThatThrownBy(() -> cardNewsService.getMemberCardNews(7L))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorStatus.PROFILE_NOT_FOUND));

        assertThat(userBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(userBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
        verify(userClient, times(16)).getMemberProfile(7L);
    }

    @Test
    void user_5xx가_반복되면_열리고_대기_뒤_시험_호출_결과로_닫히거나_다시_열린다() throws InterruptedException {
        given(userClient.getMemberProfile(7L)).willThrow(userError(500, "SERVER_001"));
        for (int i = 0; i < 10; i++) {
            assertUserUnavailable();
        }
        assertThat(userBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertUserUnavailable();
        verify(userClient, times(10)).getMemberProfile(7L);

        // 시험 호출이 실패하면 다시 OPEN
        Thread.sleep(150);
        for (int i = 0; i < 3; i++) {
            assertUserUnavailable();
        }
        assertThat(userBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(userClient, times(13)).getMemberProfile(7L);

        // 시험 호출이 성공하면 CLOSED
        Thread.sleep(150);
        willReturn(new UserApiResponse<>(true, "SUCCESS_001", "성공입니다.",
                new MemberProfileDTO(7L, LocalDate.of(2000, 3, 15),
                        new MemberProfileDTO.RegionDTO("11440", "서울특별시 마포구"), null, true, null)))
                .given(userClient).getMemberProfile(7L);
        memberProfileReader.getActiveProfile(7L);
        assertThat(userBreaker().getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        memberProfileReader.getActiveProfile(7L);
        memberProfileReader.getActiveProfile(7L);
        assertThat(userBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void engagement_회로가_열리면_부르지_않고_하트_없이_응답한다() {
        registry.circuitBreaker("policyEngagementFavorites").transitionToOpenState();
        List<String> ids = IntStream.rangeClosed(1, 45).mapToObj(i -> "p-" + i).toList();

        assertThat(favoriteReader.findFavoritePolicyIds(7L, ids)).isEmpty();
        assertThat(favoriteReader.isFavorite(7L, "p-1")).isFalse();
        verifyNoInteractions(engagementClient);
    }

    @Test
    void 청크_중간에_회로가_열려도_전체_하트를_뺀다() {
        List<String> ids = IntStream.rangeClosed(1, 45).mapToObj(i -> "p-" + i).toList();
        given(engagementClient.findActivePolicyIds(any())).willAnswer(invocation -> {
            registry.circuitBreaker("policyEngagementFavorites").transitionToOpenState();
            return ok(List.of("p-1"));
        });

        assertThat(favoriteReader.findFavoritePolicyIds(7L, ids)).isEmpty();
        verify(engagementClient, times(1)).findActivePolicyIds(any());
    }

    @Test
    void 회로는_서비스별로_따로_열린다() {
        registry.circuitBreaker("policyEngagementFavorites").transitionToOpenState();
        given(userClient.getMemberProfile(7L)).willReturn(new UserApiResponse<>(true, "SUCCESS_001", "성공입니다.",
                new MemberProfileDTO(7L, null, null, null, null, null)));

        assertThat(memberProfileReader.getActiveProfile(7L).memberId()).isEqualTo(7L);
        assertThat(userBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private void assertUserUnavailable() {
        assertThatThrownBy(() -> memberProfileReader.getActiveProfile(7L))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorStatus.USER_SERVICE_UNAVAILABLE));
    }

    private CircuitBreaker userBreaker() {
        return registry.circuitBreaker("policyUserProfile");
    }

    private static EngagementApiResponse<List<String>> ok(List<String> ids) {
        return new EngagementApiResponse<>(true, "SUCCESS_001", "성공입니다.", ids);
    }

    private static FeignException userError(int status, String code) {
        String body = "{\"isSuccess\":false,\"code\":\"" + code + "\",\"message\":\"x\",\"result\":null}";
        return FeignException.errorStatus("UserClient#getMemberProfile(Long)", Response.builder()
                .status(status)
                .request(Request.create(Request.HttpMethod.GET, "/internal/members/7/profile",
                        Map.of(), null, StandardCharsets.UTF_8, null))
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build());
    }

    private static Policy policy(String policyId) {
        Policy policy = mock(Policy.class);
        given(policy.getPolicyId()).willReturn(policyId);
        return policy;
    }
}
