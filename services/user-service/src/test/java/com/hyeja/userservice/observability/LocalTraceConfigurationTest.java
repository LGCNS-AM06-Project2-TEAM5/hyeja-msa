package com.hyeja.userservice.observability;

import static org.assertj.core.api.Assertions.assertThat;
import com.hyeja.userservice.UserServiceApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

class LocalTraceConfigurationTest {
    /** Starts the actual standalone configuration without Config Server, Eureka, Kafka or SMTP. */
    @Test
    void standaloneUserServiceStartsAndReturnsARealTraceId() throws Exception {
        try (var context = new SpringApplicationBuilder(UserServiceApplication.class)
                .registerShutdownHook(false).run(
                        "--spring.config.location=classpath:trace-local.yml",
                        "--server.port=0",
                        "--jwt.secret=test-local-trace-jwt-key-at-least-32-bytes",
                        "--spring.datasource.url=jdbc:h2:mem:trace-local-test;MODE=MariaDB;DB_CLOSE_DELAY=-1",
                        "--management.health.redis.enabled=false",
                        "--management.tracing.export.zipkin.enabled=false")) {
            String port = context.getEnvironment().getProperty("local.server.port");
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("X-Trace-Id").orElseThrow()).matches("[0-9a-f]{16}|[0-9a-f]{32}");
            assertThat(context.getEnvironment().getProperty("spring.cloud.bus.enabled")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("eureka.client.enabled")).isEqualTo("false");
        }
    }
}
