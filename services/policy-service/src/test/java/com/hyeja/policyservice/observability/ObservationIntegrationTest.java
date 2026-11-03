package com.hyeja.policyservice.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hyeja.policyservice.client.user.UserClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.tracing.sampling.probability=1.0",
        "MONITORING_PASSWORD=test-monitoring-password-1234"
})
@ActiveProfiles({"test", "debug"})
@Import(ObservationIntegrationTest.ProbeConfiguration.class)
class ObservationIntegrationTest {
    static final List<String> downstreamContexts = new CopyOnWriteArrayList<>();
    static final List<String> reportedSpans = new CopyOnWriteArrayList<>();
    static final HttpServer user = startUser();
    static final HttpServer zipkin = startZipkin();
    @LocalServerPort int port;
    @Autowired Environment environment;

    @TestConfiguration(proxyBeanMethods = false)
    @Import(ProbeController.class)
    static class ProbeConfiguration {}

    // Exists only in this test context; no diagnostic business endpoint is shipped.
    @RestController
    @org.springframework.boot.test.context.TestComponent
    static class ProbeController {
        final UserClient client;
        final RestTemplate restTemplate;
        ProbeController(UserClient client, RestTemplate restTemplate) {
            this.client = client;
            this.restTemplate = restTemplate;
        }
        @GetMapping("/__observability_test/feign")
        String feign() { client.getMemberProfile(41L); return "ok"; }
        @GetMapping("/__observability_test/rest")
        String rest() {
            restTemplate.getForObject("http://localhost:" + user.getAddress().getPort() + "/internal/members/41/profile", String.class);
            return "ok";
        }
        @GetMapping("/__observability_test/failure")
        ResponseEntity<String> failure() { return ResponseEntity.internalServerError().body("test failure"); }
    }

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.openfeign.client.config.userClient.url",
                () -> "http://localhost:" + user.getAddress().getPort());
        registry.add("management.tracing.export.zipkin.endpoint",
                () -> "http://localhost:" + zipkin.getAddress().getPort() + "/api/v2/spans");
    }

    /** Checks real HTTP transport, Feign/RestTemplate propagation, async Zipkin export and MDC/file logs. */
    @Test
    void requestContextReachesClientsZipkinAndBothLogDestinations() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        var logs = new ListAppender<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        logs.start();
        logger.addAppender(logs);
        try {
            for (String client : List.of("feign", "rest")) {
                var response = send("/__observability_test/" + client, null);
                assertThat(response.statusCode()).isEqualTo(200);
                String traceId = response.headers().firstValue("X-Trace-Id").orElseThrow();
                assertThat(traceId).matches("[0-9a-f]{16}|[0-9a-f]{32}");
                assertThat(downstreamContexts).anyMatch(header -> header.contains(traceId));
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertThat(reportedSpans).anyMatch(payload -> payload.contains(traceId) && payload.contains("CLIENT")));
                assertThat(logs.list).anySatisfy(event -> {
                    assertThat(event.getMDCPropertyMap().get("traceId")).isEqualTo(traceId);
                    assertThat(event.getMDCPropertyMap().get("spanId")).isNotBlank();
                    assertThat(event.getFormattedMessage()).contains("status=200").doesNotContain("private-query", "private-token");
                });
                assertThat(Files.readString(Path.of("logs/policy-service.log"))).contains(traceId);
            }
            assertThat(environment.getProperty("logging.logback.rollingpolicy.max-history")).isEqualTo("7");
            assertThat(environment.getProperty("logging.logback.rollingpolicy.total-size-cap")).isEqualTo("1GB");
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void authenticatedPrometheusScrapeIncludesLatencyBucketsAndHttpErrors() throws Exception {
        assertThat(send("/actuator/prometheus", null).statusCode()).isEqualTo(401);
        assertThat(send("/actuator/prometheus", "Basic bW9uaXRvcmluZzp3cm9uZw==").statusCode()).isEqualTo(401);
        assertThat(send("/__observability_test/failure", null).statusCode()).isEqualTo(500);
        String auth = "Basic " + java.util.Base64.getEncoder().encodeToString(
                "monitoring:test-monitoring-password-1234".getBytes(StandardCharsets.UTF_8));
        var response = send("/actuator/prometheus", auth);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("http_server_requests_seconds_count",
                "http_server_requests_seconds_bucket", "application=\"policy-service\"", "status=\"500\"");
    }

    HttpResponse<String> send(String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path + "?debug=private-query"))
                .header("X-Test-Secret", "private-token").timeout(Duration.ofSeconds(10)).GET();
        if (authorization != null) request.header("Authorization", authorization);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Captures propagated trace headers while returning the real UserClient response envelope. */
    static HttpServer startUser() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                downstreamContexts.add(String.valueOf(exchange.getRequestHeaders().getFirst("traceparent")));
                byte[] body = "{\"isSuccess\":true,\"code\":\"COMMON200\",\"message\":\"ok\",\"result\":null}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            server.start();
            return server;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    /** Accepts actual Zipkin exporter requests, including compressed span batches. */
    static HttpServer startZipkin() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/api/v2/spans", exchange -> {
                try (var input = "gzip".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Encoding"))
                        ? new GZIPInputStream(exchange.getRequestBody()) : exchange.getRequestBody()) {
                    reportedSpans.add(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    @AfterAll static void shutdown() { user.stop(0); zipkin.stop(0); }
}
