package com.hyeja.policyservice.client.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hyeja.policyservice.client.engagement.dto.ActivePolicyIdsRequest;
import com.hyeja.policyservice.client.engagement.dto.EngagementApiResponse;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FavoriteReaderTest {

    private final EngagementClient client = mock(EngagementClient.class);
    private final FavoriteReader reader = new FavoriteReader(client, CircuitBreakerRegistry.ofDefaults());

    @Test
    void 관심_등록한_정책_ID만_돌려준다() {
        given(client.findActivePolicyIds(new ActivePolicyIdsRequest(7L, List.of("p-1", "p-2", "p-3"))))
                .willReturn(ok(List.of("p-2")));

        assertThat(reader.findFavoritePolicyIds(7L, List.of("p-1", "p-2", "p-3"))).containsExactly("p-2");
    }

    @Test
    void 정책이_20개를_넘으면_20개씩_나눠_묻는다() {
        List<String> ids = IntStream.rangeClosed(1, 45).mapToObj(i -> "p-" + i).toList();
        given(client.findActivePolicyIds(any())).willReturn(ok(List.of()));
        given(client.findActivePolicyIds(new ActivePolicyIdsRequest(7L, ids.subList(40, 45))))
                .willReturn(ok(List.of("p-41")));

        assertThat(reader.findFavoritePolicyIds(7L, ids)).containsExactly("p-41");

        ArgumentCaptor<ActivePolicyIdsRequest> sent = ArgumentCaptor.forClass(ActivePolicyIdsRequest.class);
        verify(client, times(3)).findActivePolicyIds(sent.capture());
        assertThat(sent.getAllValues()).extracting(r -> r.policyIds().size()).containsExactly(20, 20, 5);
    }

    @Test
    void 비로그인이거나_정책이_없으면_묻지_않는다() {
        assertThat(reader.findFavoritePolicyIds(null, List.of("p-1"))).isEmpty();
        assertThat(reader.findFavoritePolicyIds(7L, List.of())).isEmpty();
        verify(client, never()).findActivePolicyIds(any());
    }

    @Test
    void engagement가_실패하면_하트_없이_응답한다() {
        given(client.findActivePolicyIds(any())).willThrow(FeignException.errorStatus("EngagementClient",
                feign.Response.builder().status(500).request(request()).headers(Map.of())
                        .body("{}", StandardCharsets.UTF_8).build()));

        assertThat(reader.findFavoritePolicyIds(7L, List.of("p-1"))).isEmpty();
        assertThat(reader.isFavorite(7L, "p-1")).isFalse();
    }

    @Test
    void 연결이_안_되거나_시간이_지나도_하트_없이_응답한다() {
        given(client.findActivePolicyIds(any())).willThrow(new RetryableException(
                -1, "Read timed out", Request.HttpMethod.POST, (Long) null, request()));

        assertThat(reader.findFavoritePolicyIds(7L, List.of("p-1"))).isEmpty();
    }

    @Test
    void 실패_응답이거나_결과가_비면_하트_없이_응답한다() {
        given(client.findActivePolicyIds(any()))
                .willReturn(new EngagementApiResponse<>(false, "SERVER_001", "x", List.of("p-1")))
                .willReturn(new EngagementApiResponse<>(true, "SUCCESS_001", "x", null));

        assertThat(reader.findFavoritePolicyIds(7L, List.of("p-1"))).isEmpty();
        assertThat(reader.findFavoritePolicyIds(7L, List.of("p-1"))).isEmpty();
    }

    @Test
    void 한_정책만_물을_때는_포함_여부로_알려준다() {
        given(client.findActivePolicyIds(new ActivePolicyIdsRequest(7L, List.of("p-1")))).willReturn(ok(List.of("p-1")));

        assertThat(reader.isFavorite(7L, "p-1")).isTrue();
        assertThat(reader.isFavorite(null, "p-1")).isFalse();
    }

    private static EngagementApiResponse<List<String>> ok(List<String> ids) {
        return new EngagementApiResponse<>(true, "SUCCESS_001", "성공입니다.", ids);
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.POST, "/internal/favorites/active-policy-ids",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }
}
