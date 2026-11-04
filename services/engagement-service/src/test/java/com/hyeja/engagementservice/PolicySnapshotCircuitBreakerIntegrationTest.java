package com.hyeja.engagementservice;

import static com.hyeja.engagementservice.security.InternalTokenFixture.valid;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hyeja.engagementservice.client.policy.CircuitBreakerIgnorePredicate;
import com.hyeja.engagementservice.client.policy.PolicySnapshotReader;
import com.hyeja.engagementservice.favorite.repository.FavoriteRepository;
import com.hyeja.engagementservice.notification.repository.NotificationRepository;
import com.hyeja.engagementservice.observability.CircuitBreakerLoggingConfiguration;
import com.hyeja.engagementservice.policy.entity.Policy;
import com.hyeja.engagementservice.policy.enums.PolicyCategory;
import com.hyeja.engagementservice.policy.repository.PolicyRepository;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
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

// 실제 Feign·Circuit Breaker·외부 API(POST /api/favorite/{policyId})를 거쳐 F5 장애를 재현합니다. policy 대신 JDK HttpServer를 띄웁니다.
// 회로 설정은 application.yml 그대로이고, Timeout 시험 시간을 줄이려고 read timeout(200ms)만 바꿉니다.
// 대기 시간 경과 뒤 HALF_OPEN 전환은 PolicySnapshotCircuitBreakerTest에서 짧은 대기 시간으로 확인합니다.
@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MONITORING_PASSWORD=test-monitoring-password-1234")
@ActiveProfiles("test")
class PolicySnapshotCircuitBreakerIntegrationTest {
    private static final String SUCCESS = """
            {"isSuccess":true,"code":"SUCCESS_001","message":"성공","result":{
              "policyId":"F5-CB","policyName":"최신 정책","categories":["MONTHLY_RENT"],
              "supportContent":null,"applyEndDate":"2026-01-01","applyPeriodCode":"CLOSED",
              "applyUrl":null,"activeYn":true,"deleted":false}}
            """;
    private static final AtomicInteger hits = new AtomicInteger();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final HttpServer server = startServer();
    private static volatile int status = 200;
    private static volatile String body = SUCCESS;
    private static volatile long delayMillis;

    @Autowired PolicySnapshotReader reader;
    @Autowired CircuitBreakerRegistry registry;
    @Autowired PolicyRepository policies;
    @Autowired FavoriteRepository favorites;
    @Autowired NotificationRepository notifications;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("clients.policy.url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
        properties.add("spring.cloud.openfeign.client.config.policy-service.read-timeout", () -> 200);
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:f5-circuit;MODE=MariaDB;DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @BeforeEach
    void reset() {
        registry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        notifications.deleteAll();
        favorites.deleteAll();
        policies.deleteAll();
        hits.set(0);
        status = 200;
        body = SUCCESS;
        delayMillis = 0;
    }

    @Test
    void F5는_F1부터_F6과_같은_초기_설정의_독립_회로를_쓴다() {
        CircuitBreakerConfig config = breaker().getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(config.getSlidingWindowSize()).isEqualTo(20);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(Duration.ofSeconds(15).toMillis());
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isFalse();
        assertThat(config.getIgnoreExceptionPredicate()).isInstanceOf(CircuitBreakerIgnorePredicate.class);
    }

    @Test
    void 정상_응답이면_스냅샷을_만들고_관심_정책을_등록한다() throws Exception {
        HttpResponse<String> response = register();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"isSuccess\":true", "\"code\":\"SUCCESS_001\"");
        assertThat(policies.findById("F5-CB").orElseThrow().getPolicyName()).isEqualTo("최신 정책");
        assertThat(favorites.count()).isEqualTo(1);
        assertThat(breaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void POLICY_001은_404로_그대로_응답하고_회로를_열지_않는다() throws Exception {
        status = 404;
        body = "{\"isSuccess\":false,\"code\":\"POLICY_001\",\"message\":\"없음\",\"result\":null}";
        for (int i = 0; i < 20; i++) {
            HttpResponse<String> response = register();
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).contains("\"code\":\"POLICY_001\"");
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker().getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(hits).hasValue(20);
    }

    @Test
    void 그_밖의_4xx는_기존대로_503_SERVER_002이고_회로를_열지_않는다() throws Exception {
        body = "{\"isSuccess\":false,\"code\":\"COMMON_002\",\"message\":\"x\",\"result\":null}";
        for (int i = 0; i < 20; i++) {
            status = i % 2 == 0 ? 401 : 403;
            assertUnavailable(register());
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker().getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    void 서버_오류가_반복되면_열리고_열린_동안은_부르지_않고_503_SERVER_002로_응답하며_DB를_바꾸지_않는다() throws Exception {
        saveStaleSnapshot();
        Logger logger = (Logger) LoggerFactory.getLogger(CircuitBreakerLoggingConfiguration.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        status = 500;
        body = "{\"isSuccess\":false,\"code\":\"SERVER_001\",\"message\":\"x\",\"result\":null}";
        try {
            for (int i = 0; i < 10; i++) {
                assertUnavailable(register());
            }
        } finally {
            logger.detachAppender(logs);
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Circuit breaker state changed. name=engPolicySnapshot, transition=State transition from CLOSED to OPEN");

        // policy가 회복해도 OPEN 동안은 호출하지 않고, 기존 스냅샷으로 대신 등록하지 않습니다.
        status = 200;
        body = SUCCESS;
        assertUnavailable(register());
        assertThat(hits).hasValue(10);
        assertStaleSnapshotUnchanged();
        assertThat(favorites.count()).isZero();
    }

    @Test
    void Timeout과_역직렬화_실패는_503_SERVER_002로_응답하고_실패로_센다() throws Exception {
        saveStaleSnapshot();
        delayMillis = 1000;
        assertUnavailable(register());
        delayMillis = 0;
        body = "not json";
        assertUnavailable(register());

        assertThat(breaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
        assertStaleSnapshotUnchanged();
        assertThat(favorites.count()).isZero();
    }

    // Compose healthcheck(curl --fail /actuator/health)가 F5 회로 때문에 이 서비스를 내리지 않아야 합니다.
    @Test
    void 회로가_열려도_health는_UP이다() throws Exception {
        breaker().transitionToOpenState();

        HttpResponse<String> health = get("/actuator/health", null);

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void 회로_상태와_성공_실패_제외_차단_호출이_인증된_Prometheus에_노출된다() throws Exception {
        assertThat(get("/actuator/prometheus", null).statusCode()).isEqualTo(401);
        String before = scrape();

        reader.getPolicy("F5-CB");
        status = 404;
        body = "{\"isSuccess\":false,\"code\":\"POLICY_001\",\"message\":\"없음\",\"result\":null}";
        register();
        status = 500;
        body = "{\"isSuccess\":false,\"code\":\"SERVER_001\",\"message\":\"x\",\"result\":null}";
        // 성공 1 + 실패 9 = 기록 10회(제외된 404는 세지 않음), 실패율 90%로 OPEN
        for (int i = 0; i < 9; i++) {
            register();
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        register();

        String after = scrape();
        assertThat(after).contains("application=\"engagement-service\"");
        assertThat(metric(after, "resilience4j_circuitbreaker_state", "name=\"engPolicySnapshot\"", "state=\"open\""))
                .isEqualTo(1.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_calls_seconds_count",
                "name=\"engPolicySnapshot\"", "kind=\"successful\"")).isEqualTo(1.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_calls_seconds_count",
                "name=\"engPolicySnapshot\"", "kind=\"ignored\"")).isEqualTo(1.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_calls_seconds_count",
                "name=\"engPolicySnapshot\"", "kind=\"failed\"")).isEqualTo(9.0);
        assertThat(increase(before, after, "resilience4j_circuitbreaker_not_permitted_calls_total",
                "name=\"engPolicySnapshot\"")).isEqualTo(1.0);
    }

    private CircuitBreaker breaker() {
        return registry.circuitBreaker("engPolicySnapshot");
    }

    private static void assertUnavailable(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"isSuccess\":false", "\"code\":\"SERVER_002\"");
    }

    private void saveStaleSnapshot() {
        Policy policy = new Policy();
        policy.setPolicyId("F5-CB");
        policy.setPolicyName("이전 정책");
        policy.setCategories(Set.of(PolicyCategory.OTHER));
        policy.setActiveYn(false);
        policies.saveAndFlush(policy);
    }

    private void assertStaleSnapshotUnchanged() {
        Policy policy = policies.findById("F5-CB").orElseThrow();
        assertThat(policy.getPolicyName()).isEqualTo("이전 정책");
        assertThat(policy.getActiveYn()).isFalse();
        assertThat(policy.getCategories()).containsExactly(PolicyCategory.OTHER);
    }

    private HttpResponse<String> register() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/favorite/F5-CB"))
                .timeout(Duration.ofSeconds(10))
                .header("X-Gateway-Assertion", valid(1))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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

    private static HttpServer startServer() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.setExecutor(executor);
            http.createContext("/internal/policies/", exchange -> {
                hits.incrementAndGet();
                int responseStatus = status;
                byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
                long delay = delayMillis;
                try (exchange) {
                    if (delay > 0) Thread.sleep(delay);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(responseStatus, responseBody.length);
                    exchange.getResponseBody().write(responseBody);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (IOException clientDisconnected) {
                    // 타임아웃 뒤 클라이언트가 연결을 닫는 것은 예상한 동작입니다.
                }
            });
            http.start();
            return http;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
