package com.hyeja.policyservice.observability;

import static org.assertj.core.api.Assertions.assertThat;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SafeFeignLoggerTest {
    @Test
    void fullLoggingRedactsSecretsWithoutConsumingTheResponseBody() throws Exception {
        Logger output = (Logger) LoggerFactory.getLogger(SafeFeignLogger.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        Level previous = output.getLevel();
        appender.start();
        output.addAppender(appender);
        output.setLevel(Level.DEBUG);
        try {
            var request = Request.create(Request.HttpMethod.POST, "http://example.test/private-id?key=secret-query",
                    Map.of("Authorization", List.of("Bearer secret-token")),
                    "secret-request".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, new RequestTemplate());
            var response = Response.builder().request(request).status(200).reason("OK")
                    .headers(Map.of("Set-Cookie", List.of("secret-cookie")))
                    .body("secret-response", StandardCharsets.UTF_8).build();
            var logger = new SafeFeignLogger();
            logger.logRequest("UserClient#getProfile(Long)", feign.Logger.Level.FULL, request);
            var returned = logger.logAndRebufferResponse("UserClient#getProfile(Long)", feign.Logger.Level.FULL, response, 10);
            logger.logIOException("UserClient#getProfile(Long)", feign.Logger.Level.FULL,
                    new java.io.IOException("secret-exception"), 10);
            assertThat(new java.io.BufferedReader(returned.body().asReader(StandardCharsets.UTF_8)).readLine()).isEqualTo("secret-response");
            String logs = appender.list.stream().map(event -> event.getFormattedMessage())
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs).contains("POST", "status=200", "durationMs=10", "IOException")
                    .doesNotContain("secret-", "private-id");
        } finally {
            output.detachAppender(appender);
            output.setLevel(previous);
            appender.stop();
        }
    }

    @Test
    void fullLevelIsOptInAndTheSafeLoggerIsAlwaysUsed() {
        try (var context = new AnnotationConfigApplicationContext(FeignLoggingConfiguration.class)) {
            assertThat(context.getBeansOfType(feign.Logger.Level.class)).isEmpty();
            assertThat(context.getBean(feign.Logger.class)).isInstanceOf(SafeFeignLogger.class);
        }
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("debug");
            context.register(FeignLoggingConfiguration.class);
            context.refresh();
            assertThat(context.getBean(feign.Logger.Level.class)).isEqualTo(feign.Logger.Level.FULL);
        }
    }
}
