package com.hyeja.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hyeja.gateway.security.EngagementTokenIssuer;
import com.hyeja.gateway.security.JwtVerifier;
import com.hyeja.gateway.security.SecurityConfig;
import com.hyeja.gateway.security.TokenBlacklist;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
@org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
@SpringBootTest(classes = GatewayObservationIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:trace-local.yml",
        "spring.application.name=gateway-service", "spring.reactor.context-propagation=auto",
        "spring.cloud.config.enabled=false", "spring.cloud.bus.enabled=false",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "jwt.secret=test-gateway-user-key-at-least-32-bytes-long",
        "internal-auth.secret=test-gateway-internal-key-at-least-32-bytes-long",
        "management.tracing.sampling.probability=1.0",
        "management.tracing.export.zipkin.enabled=false",
        "MONITORING_PASSWORD=test-monitoring-password-1234"
})
class GatewayObservationIntegrationTest {
    static final List<String> downstreamContexts = new CopyOnWriteArrayList<>();
    static final HttpServer downstream = startDownstream();
    @LocalServerPort int port;

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, JwtVerifier.class, TokenBlacklist.class, EngagementTokenIssuer.class,
            MonitoringSecurityConfiguration.class, RequestLoggingFilter.class})
    static class TestApplication {
        @Bean RouteLocator probeRoute(RouteLocatorBuilder builder) {
            return builder.routes().route("observability-test", route -> route.order(-100)
                    .path("/api/terms/observability/**")
                    .uri("http://localhost:" + downstream.getAddress().getPort())).build();
        }
    }

    /** Exercises the real reactive Gateway transport, response trace header and Reactor MDC restoration. */
    @Test
    void gatewayForwardsTraceContextAndCorrelatesSuccessAndFailureLogs() throws Exception {
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
            for (String outcome : List.of("ok", "failure")) {
                var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                                + "/api/terms/observability/" + outcome + "?secret=private-query"))
                        .timeout(Duration.ofSeconds(10)).GET().build();
                var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(outcome.equals("ok") ? 200 : 500);
                String traceId = response.headers().firstValue("X-Trace-Id").orElseThrow();
                assertThat(downstreamContexts).anyMatch(value -> value.contains(traceId));
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                        assertThat(logs.list).anySatisfy(event -> {
                            assertThat(event.getMDCPropertyMap().get("traceId")).isEqualTo(traceId);
                            assertThat(event.getMDCPropertyMap().get("spanId")).isNotBlank();
                            assertThat(event.getFormattedMessage()).contains("route=observability-test",
                                    "status=" + response.statusCode()).doesNotContain("private-query");
                        }));
            }
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    /** Represents an upstream service that exposes received trace context without external dependencies. */
    static HttpServer startDownstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                downstreamContexts.add(String.valueOf(exchange.getRequestHeaders().getFirst("traceparent")));
                exchange.sendResponseHeaders(exchange.getRequestURI().getPath().endsWith("failure") ? 500 : 200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    @AfterAll static void shutdown() { downstream.stop(0); }
}
