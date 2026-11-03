package com.hyeja.recommendationservice.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hyeja.recommendationservice.favorite.client.FavoriteClient;
import com.hyeja.recommendationservice.observability.CircuitBreakerLoggingConfiguration;
import com.hyeja.recommendationservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.recommendationservice.global.exception.GeneralException;
import com.hyeja.recommendationservice.policy.client.PolicyClient;
import com.hyeja.recommendationservice.policy.enums.PolicyCategory;
import com.hyeja.recommendationservice.profile.client.ProfileClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// 실제 Feign·UserServiceErrorDecoder·Circuit Breaker를 거쳐 F1·F2·F3 장애와 복구를 재현합니다.
// user·policy 대신 JDK HttpServer를 띄우고, engagement는 아무도 듣지 않는 포트로 돌려 연결 실패를 만듭니다.
// 시험 시간을 줄이려고 OPEN 대기 시간(300ms)과 read timeout(500ms)만 바꾸고 나머지 회로 설정은 application.yml 값을 씁니다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=300ms",
        "spring.cloud.openfeign.client.config.default.read-timeout=500",
        "management.prometheus.metrics.export.enabled=true",
        "MONITORING_PASSWORD=test-monitoring-password-1234"
})
@ActiveProfiles("test")
class FeignCircuitBreakerIntegrationTest {

    private static final Map<String, String> MODES = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();
    private static final HttpServer DOWNSTREAM = startDownstream();
    private static final int CLOSED_PORT = closedPort();

    @Autowired ProfileClient profileClient;
    @Autowired PolicyClient policyClient;
    @Autowired FavoriteClient favoriteClient;
    @Autowired CircuitBreakerRegistry registry;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry properties) {
        String url = "http://localhost:" + DOWNSTREAM.getAddress().getPort();
        properties.add("spring.cloud.openfeign.client.config.user-service.url", () -> url);
        properties.add("spring.cloud.openfeign.client.config.policy-service.url", () -> url);
        properties.add("spring.cloud.openfeign.client.config.engagement-service.url",
                () -> "http://localhost:" + CLOSED_PORT);
    }

    @AfterAll
    static void stop() {
        DOWNSTREAM.stop(0);
    }

    @BeforeEach
    void reset() {
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        MODES.put("user", "OK");
        MODES.put("policy", "OK");
        HITS.values().forEach(hits -> hits.set(0));
    }

    @Test
    void 정상_응답은_그대로_옮기고_회로는_닫혀_있다() {
        assertThat(profileClient.getActiveProfile(1L).eligibility().getRegion().getRegionCode()).isEqualTo("11440");
        assertThat(searchPolicies()).singleElement()
                .satisfies(policy -> assertThat(policy.policyId()).isEqualTo("P-1"));

        assertThat(breaker("recUserProfile").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker("recUserProfile").getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(breaker("recPolicyCandidates").getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void 정상_후보_0건은_빈_목록이고_실패로_세지_않는다() {
        MODES.put("policy", "EMPTY");

        assertThat(searchPolicies()).isEmpty();
        assertThat(breaker("recPolicyCandidates").getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(breaker("recPolicyCandidates").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void MEMBER_001과_PROFILE_001은_그대로_전달되고_회로를_열지_않는다() {
        for (int i = 0; i < 20; i++) {
            MODES.put("user", i % 2 == 0 ? "MEMBER_404" : "PROFILE_404");
            ErrorStatus expected = i % 2 == 0 ? ErrorStatus.MEMBER_NOT_FOUND : ErrorStatus.PROFILE_NOT_FOUND;
            assertThatThrownBy(() -> profileClient.getActiveProfile(1L))
                    .isInstanceOfSatisfying(GeneralException.class,
                            exception -> assertThat(exception.getCode()).isEqualTo(expected));
        }

        CircuitBreaker breaker = breaker("recUserProfile");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(HITS.get("user")).hasValue(20);
    }

    @Test
    void 다른_4xx도_실패율에_넣지_않는다() {
        MODES.put("policy", "BAD_REQUEST");
        for (int i = 0; i < 12; i++) {
            assertThatThrownBy(this::searchPolicies).isInstanceOf(FeignException.BadRequest.class);
        }

        assertThat(breaker("recPolicyCandidates").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker("recPolicyCandidates").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void 서버_오류가_반복되면_열리고_열린_동안은_정책_서비스를_부르지_않는다() {
        Logger logger = (Logger) LoggerFactory.getLogger(CircuitBreakerLoggingConfiguration.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        MODES.put("policy", "SERVER_ERROR");
        try {
            for (int i = 0; i < 10; i++) {
                assertThatThrownBy(this::searchPolicies).isInstanceOf(FeignException.InternalServerError.class);
            }
        } finally {
            logger.detachAppender(logs);
        }
        assertThat(breaker("recPolicyCandidates").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .contains("Circuit breaker state changed. name=recPolicyCandidates, transition=State transition from CLOSED to OPEN");

        MODES.put("policy", "OK");
        assertThatThrownBy(this::searchPolicies).isInstanceOf(CallNotPermittedException.class);
        assertThat(HITS.get("policy")).hasValue(10);
        // 같은 서비스의 다른 회로(F1)는 영향을 받지 않습니다.
        assertThat(profileClient.getActiveProfile(1L)).isNotNull();
        assertThat(breaker("recUserProfile").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void 대기_시간이_지나면_반열림에서_시험_호출이_성공하면_닫힌다() throws InterruptedException {
        openUserBreaker();
        Thread.sleep(400);
        MODES.put("user", "OK");

        profileClient.getActiveProfile(1L);
        assertThat(breaker("recUserProfile").getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        profileClient.getActiveProfile(1L);
        profileClient.getActiveProfile(1L);

        assertThat(breaker("recUserProfile").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(HITS.get("user")).hasValue(13);
    }

    @Test
    void 반열림_시험_호출이_실패하면_다시_열린다() throws InterruptedException {
        openUserBreaker();
        Thread.sleep(400);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> profileClient.getActiveProfile(1L)).isInstanceOf(FeignException.class);
        }

        assertThat(breaker("recUserProfile").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> profileClient.getActiveProfile(1L)).isInstanceOf(CallNotPermittedException.class);
        assertThat(HITS.get("user")).hasValue(13);
    }

    @Test
    void 응답_지연은_기존_read_timeout으로_끊기고_실패로_센다() {
        MODES.put("user", "SLOW");

        assertThatThrownBy(() -> profileClient.getActiveProfile(1L)).isInstanceOf(RetryableException.class);

        assertThat(breaker("recUserProfile").getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void 연결_실패가_반복되면_관심_회로만_열린다() {
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> favoriteClient.findActivePolicyIds(1L, List.of("P-1")))
                    .isInstanceOf(RetryableException.class);
        }

        assertThat(breaker("recEngagementFavorites").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> favoriteClient.findActivePolicyIds(1L, List.of("P-1")))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(breaker("recUserProfile").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker("recPolicyCandidates").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(searchPolicies()).hasSize(1);
    }

    // Compose healthcheck(curl --fail /actuator/health)가 하위 서비스 회로 때문에 이 서비스를 내리지 않아야 합니다.
    @Test
    void 회로가_모두_열려도_health는_UP이다() throws Exception {
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::transitionToOpenState);

        HttpResponse<String> health = get("/actuator/health", null);

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void 회로_상태와_성공_실패_제외_차단_호출이_Prometheus에_노출된다() throws Exception {
        assertThat(get("/actuator/prometheus", null).statusCode()).isEqualTo(401);
        // 호출 수 메트릭은 같은 컨텍스트의 다른 테스트 호출까지 누적되므로 전후 차이로 봅니다.
        String before = scrape();

        profileClient.getActiveProfile(1L);
        MODES.put("user", "MEMBER_404");
        assertThatThrownBy(() -> profileClient.getActiveProfile(1L)).isInstanceOf(GeneralException.class);
        MODES.put("policy", "SERVER_ERROR");
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(this::searchPolicies).isInstanceOf(FeignException.class);
        }
        assertThatThrownBy(this::searchPolicies).isInstanceOf(CallNotPermittedException.class);

        String after = scrape();
        assertThat(after).contains("application=\"recommendation-service\"");
        assertThat(metric(after, "resilience4j_circuitbreaker_state", "name=\"recUserProfile\"", "state=\"closed\""))
                .isEqualTo(1.0);
        assertThat(metric(after, "resilience4j_circuitbreaker_state", "name=\"recPolicyCandidates\"", "state=\"open\""))
                .isEqualTo(1.0);
        assertThat(metric(after, "resilience4j_circuitbreaker_state", "name=\"recEngagementFavorites\"", "state=\"closed\""))
                .isEqualTo(1.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_calls_seconds_count",
                "name=\"recUserProfile\"", "kind=\"successful\"")).isEqualTo(1.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_calls_seconds_count",
                "name=\"recUserProfile\"", "kind=\"ignored\"")).isEqualTo(1.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_calls_seconds_count",
                "name=\"recPolicyCandidates\"", "kind=\"failed\"")).isEqualTo(10.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_not_permitted_calls_total",
                "name=\"recPolicyCandidates\"")).isEqualTo(1.0);
    }

    private String scrape() throws Exception {
        HttpResponse<String> response = get("/actuator/prometheus", "Basic " + Base64.getEncoder().encodeToString(
                "monitoring:test-monitoring-password-1234".getBytes(StandardCharsets.UTF_8)));
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private static double increase(String before, String after, String name, String... labels) {
        return metric(after, name, labels) - metric(before, name, labels);
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static double metric(String scrape, String name, String... labels) {
        return scrape.lines()
                .filter(line -> line.startsWith(name + "{"))
                .filter(line -> java.util.Arrays.stream(labels).allMatch(line::contains))
                .map(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " " + List.of(labels) + " not found in:\n" + scrape));
    }

    private List<com.hyeja.recommendationservice.policy.dto.SearchPolicyDTO> searchPolicies() {
        return policyClient.searchActivePolicies(
                Set.of(PolicyCategory.MONTHLY_RENT), "11440", LocalDate.of(2026, 11, 3), 20);
    }

    private void openUserBreaker() {
        MODES.put("user", "SERVER_ERROR");
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> profileClient.getActiveProfile(1L)).isInstanceOf(FeignException.class);
        }
        assertThat(breaker("recUserProfile").getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    private CircuitBreaker breaker(String name) {
        return registry.circuitBreaker(name);
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/internal/members/", exchange -> handle(exchange, "user"));
            server.createContext("/internal/policies/candidates", exchange -> handle(exchange, "policy"));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange, String service) throws IOException {
        HITS.computeIfAbsent(service, key -> new AtomicInteger()).incrementAndGet();
        String mode = MODES.getOrDefault(service, "OK");
        switch (mode) {
            case "SERVER_ERROR" -> respond(exchange, 500,
                    "{\"isSuccess\":false,\"code\":\"SERVER_001\",\"message\":\"x\",\"result\":null}");
            case "BAD_REQUEST" -> respond(exchange, 400,
                    "{\"isSuccess\":false,\"code\":\"COMMON_001\",\"message\":\"x\",\"result\":null}");
            case "MEMBER_404" -> respond(exchange, 404,
                    "{\"isSuccess\":false,\"code\":\"MEMBER_001\",\"message\":\"x\",\"result\":null}");
            case "PROFILE_404" -> respond(exchange, 404,
                    "{\"isSuccess\":false,\"code\":\"PROFILE_001\",\"message\":\"x\",\"result\":null}");
            case "EMPTY" -> respond(exchange, 200,
                    "{\"isSuccess\":true,\"code\":\"SUCCESS_001\",\"message\":\"성공입니다.\",\"result\":[]}");
            case "SLOW" -> {
                sleep(1500);
                respond(exchange, 200, okBody(service));
            }
            default -> respond(exchange, 200, okBody(service));
        }
    }

    private static String okBody(String service) {
        String result = service.equals("user")
                ? "{\"memberId\":1,\"birth\":\"2000-03-15\",\"region\":{\"regionCode\":\"11440\",\"sigunguName\":\"서울특별시 마포구\"},"
                + "\"employmentCode\":\"EMPLOYED\",\"houselessYn\":true,\"marriageCode\":null,\"incomeRangeCode\":null,"
                + "\"educationCode\":null,\"housingType\":null}"
                : "[{\"policyId\":\"P-1\",\"policyName\":\"청년 월세 지원\",\"categories\":[\"MONTHLY_RENT\"],"
                + "\"applyPeriodCode\":\"ALWAYS\",\"regions\":[]}]";
        return "{\"isSuccess\":true,\"code\":\"SUCCESS_001\",\"message\":\"성공입니다.\",\"result\":" + result + "}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException ignored) {
            // read timeout으로 클라이언트가 먼저 끊은 경우입니다.
        } finally {
            exchange.close();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
