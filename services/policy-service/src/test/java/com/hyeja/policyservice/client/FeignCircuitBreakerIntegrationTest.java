package com.hyeja.policyservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hyeja.policyservice.client.engagement.FavoriteReader;
import com.hyeja.policyservice.client.user.MemberProfileReader;
import com.hyeja.policyservice.global.apiPayload.status.ErrorStatus;
import com.hyeja.policyservice.global.exception.GeneralException;
import com.hyeja.policyservice.observability.CircuitBreakerLoggingConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
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

// 실제 Feign·Circuit Breaker를 거쳐 F4·F6 장애를 재현합니다. user·engagement 대신 JDK HttpServer를 띄웁니다.
// 회로 설정은 application.yml 그대로이고, Timeout 시험 시간을 줄이려고 read timeout(500ms)만 바꿉니다.
// 대기 시간 경과 뒤 HALF_OPEN 전환은 CircuitBreakerFallbackTest에서 짧은 대기 시간으로 확인합니다.
@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.openfeign.client.config.default.read-timeout=500",
        "MONITORING_PASSWORD=test-monitoring-password-1234"
})
@ActiveProfiles("test")
class FeignCircuitBreakerIntegrationTest {

    private static final Map<String, String> MODES = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> HITS = new ConcurrentHashMap<>();
    private static final HttpServer DOWNSTREAM = startDownstream();
    private static final List<String> IDS = IntStream.rangeClosed(1, 45).mapToObj(i -> "p-" + i).toList();

    @Autowired MemberProfileReader memberProfileReader;
    @Autowired FavoriteReader favoriteReader;
    @Autowired CircuitBreakerRegistry registry;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry properties) {
        String url = "http://localhost:" + DOWNSTREAM.getAddress().getPort();
        properties.add("spring.cloud.openfeign.client.config.userClient.url", () -> url);
        properties.add("spring.cloud.openfeign.client.config.engagementClient.url", () -> url);
    }

    @AfterAll
    static void stop() {
        DOWNSTREAM.stop(0);
    }

    @BeforeEach
    void reset() {
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        MODES.put("user", "OK");
        MODES.put("engagement", "OK");
        HITS.values().forEach(hits -> hits.set(0));
    }

    @Test
    void F4와_F6는_같은_초기_설정의_독립_회로를_쓴다() {
        assertThat(registry.getAllCircuitBreakers()).extracting(CircuitBreaker::getName)
                .contains("policyEngagementFavorites", "policyUserProfile");
        assertThat(breaker("policyEngagementFavorites")).isNotSameAs(breaker("policyUserProfile"));
        for (String name : List.of("policyEngagementFavorites", "policyUserProfile")) {
            CircuitBreakerConfig config = breaker(name).getCircuitBreakerConfig();
            assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
            assertThat(config.getSlidingWindowSize()).isEqualTo(20);
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
            assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
            assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(Duration.ofSeconds(15).toMillis());
            assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
            assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isFalse();
            assertThat(config.getIgnoreExceptionPredicate()).isInstanceOf(CircuitBreakerIgnorePredicate.class);
        }
    }

    @Test
    void 정상_응답은_그대로_옮긴다() {
        assertThat(memberProfileReader.getActiveProfile(1L).region().regionCode()).isEqualTo("11440");
        assertThat(favoriteReader.findFavoritePolicyIds(1L, IDS)).containsExactly("p-1");

        assertThat(breaker("policyUserProfile").getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(breaker("policyEngagementFavorites").getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(3);
    }

    @Test
    void MEMBER_001과_PROFILE_001은_그대로_전달되고_회로를_열지_않는다() {
        for (int i = 0; i < 20; i++) {
            MODES.put("user", i % 2 == 0 ? "MEMBER_404" : "PROFILE_404");
            ErrorStatus expected = i % 2 == 0 ? ErrorStatus.MEMBER_NOT_FOUND : ErrorStatus.PROFILE_NOT_FOUND;
            assertThatThrownBy(() -> memberProfileReader.getActiveProfile(1L))
                    .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getCode()).isEqualTo(expected));
        }

        assertThat(breaker("policyUserProfile").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker("policyUserProfile").getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(HITS.get("user")).hasValue(20);
    }

    @Test
    void user_서버_오류가_반복되면_열리고_열린_동안은_부르지_않고_SERVER_002로_바꾼다() {
        Logger logger = (Logger) LoggerFactory.getLogger(CircuitBreakerLoggingConfiguration.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        MODES.put("user", "SERVER_ERROR");
        try {
            for (int i = 0; i < 10; i++) {
                assertUserUnavailable();
            }
        } finally {
            logger.detachAppender(logs);
        }
        assertThat(breaker("policyUserProfile").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .contains("Circuit breaker state changed. name=policyUserProfile, transition=State transition from CLOSED to OPEN");

        MODES.put("user", "OK");
        assertUserUnavailable();
        assertThat(HITS.get("user")).hasValue(10);
        // engagement 회로(F4)는 영향을 받지 않습니다.
        assertThat(favoriteReader.findFavoritePolicyIds(1L, List.of("p-1"))).containsExactly("p-1");
        assertThat(breaker("policyEngagementFavorites").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void user_응답_지연은_기존_read_timeout으로_끊고_SERVER_002로_바꾸며_실패로_센다() {
        MODES.put("user", "SLOW");

        assertUserUnavailable();

        assertThat(breaker("policyUserProfile").getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void engagement_오류가_반복되면_열리고_열린_동안은_부르지_않고_하트_없이_응답한다() {
        MODES.put("engagement", "SERVER_ERROR");
        for (int i = 0; i < 10; i++) {
            assertThat(favoriteReader.findFavoritePolicyIds(1L, List.of("p-1"))).isEmpty();
        }
        assertThat(breaker("policyEngagementFavorites").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        MODES.put("engagement", "OK");
        assertThat(favoriteReader.findFavoritePolicyIds(1L, IDS)).isEmpty();
        assertThat(favoriteReader.isFavorite(1L, "p-1")).isFalse();
        assertThat(HITS.get("engagement")).hasValue(10);
        // user 회로(F6)는 영향을 받지 않습니다.
        assertThat(memberProfileReader.getActiveProfile(1L).memberId()).isEqualTo(1L);
        assertThat(breaker("policyUserProfile").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void 두_번째_청크가_실패하면_전체_하트를_빼고_실패_한_번만_센다() {
        MODES.put("engagement", "FAIL_SECOND_CHUNK");

        assertThat(favoriteReader.findFavoritePolicyIds(1L, IDS)).isEmpty();

        assertThat(HITS.get("engagement")).hasValue(2);
        assertThat(breaker("policyEngagementFavorites").getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(breaker("policyEngagementFavorites").getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
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

        favoriteReader.findFavoritePolicyIds(1L, List.of("p-1"));
        MODES.put("user", "PROFILE_404");
        assertThatThrownBy(() -> memberProfileReader.getActiveProfile(1L)).isInstanceOf(GeneralException.class);
        MODES.put("user", "SERVER_ERROR");
        for (int i = 0; i < 10; i++) {
            assertUserUnavailable();
        }
        assertUserUnavailable();

        String after = scrape();
        assertThat(after).contains("application=\"policy-service\"");
        assertThat(metric(after, "resilience4j_circuitbreaker_state", "name=\"policyUserProfile\"", "state=\"open\""))
                .isEqualTo(1.0);
        assertThat(metric(after, "resilience4j_circuitbreaker_state", "name=\"policyEngagementFavorites\"", "state=\"closed\""))
                .isEqualTo(1.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_calls_seconds_count",
                "name=\"policyEngagementFavorites\"", "kind=\"successful\"")).isEqualTo(1.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_calls_seconds_count",
                "name=\"policyUserProfile\"", "kind=\"ignored\"")).isEqualTo(1.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_calls_seconds_count",
                "name=\"policyUserProfile\"", "kind=\"failed\"")).isEqualTo(10.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_not_permitted_calls_total",
                "name=\"policyUserProfile\"")).isEqualTo(1.0);
    }

    private void assertUserUnavailable() {
        assertThatThrownBy(() -> memberProfileReader.getActiveProfile(1L))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorStatus.USER_SERVICE_UNAVAILABLE));
    }

    private CircuitBreaker breaker(String name) {
        return registry.circuitBreaker(name);
    }

    private String scrape() throws Exception {
        HttpResponse<String> response = get("/actuator/prometheus", "Basic " + Base64.getEncoder().encodeToString(
                "monitoring:test-monitoring-password-1234".getBytes(StandardCharsets.UTF_8)));
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static double increase(String before, String after, String name, String... labels) {
        return metric(after, name, labels) - metric(before, name, labels);
    }

    private static double metric(String scrape, String name, String... labels) {
        return scrape.lines()
                .filter(line -> line.startsWith(name + "{"))
                .filter(line -> java.util.Arrays.stream(labels).allMatch(line::contains))
                .map(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " " + List.of(labels) + " not found in:\n" + scrape));
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/internal/members/", exchange -> handle(exchange, "user"));
            server.createContext("/internal/favorites/active-policy-ids", exchange -> handle(exchange, "engagement"));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange, String service) throws IOException {
        HITS.computeIfAbsent(service, key -> new AtomicInteger()).incrementAndGet();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String mode = MODES.getOrDefault(service, "OK");
        if (mode.equals("FAIL_SECOND_CHUNK")) {
            mode = body.contains("\"p-21\"") ? "SERVER_ERROR" : "OK";
        }
        switch (mode) {
            case "SERVER_ERROR" -> respond(exchange, 500,
                    "{\"isSuccess\":false,\"code\":\"SERVER_001\",\"message\":\"x\",\"result\":null}");
            case "MEMBER_404" -> respond(exchange, 404,
                    "{\"isSuccess\":false,\"code\":\"MEMBER_001\",\"message\":\"x\",\"result\":null}");
            case "PROFILE_404" -> respond(exchange, 404,
                    "{\"isSuccess\":false,\"code\":\"PROFILE_001\",\"message\":\"x\",\"result\":null}");
            case "SLOW" -> {
                sleep(1500);
                respond(exchange, 200, okBody(service, body));
            }
            default -> respond(exchange, 200, okBody(service, body));
        }
    }

    private static String okBody(String service, String requestBody) {
        String result = service.equals("user")
                ? "{\"memberId\":1,\"birth\":\"2000-03-15\",\"region\":{\"regionCode\":\"11440\",\"sigunguName\":\"서울특별시 마포구\"},"
                + "\"employmentCode\":\"EMPLOYED\",\"houselessYn\":true,\"incomeRangeCode\":null}"
                : requestBody.contains("\"p-1\"") ? "[\"p-1\"]" : "[]";
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
}
