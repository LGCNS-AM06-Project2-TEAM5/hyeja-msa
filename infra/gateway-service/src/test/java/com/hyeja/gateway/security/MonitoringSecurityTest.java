package com.hyeja.gateway.security;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.hyeja.gateway.observability.MonitoringSecurityConfiguration;

class MonitoringSecurityTest {
    @Test
    void monitoringBasicAuthIsLimitedToReadOnlyMetricsAndCannotBypassJwt() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("monitoring-test",
                    Map.of("MONITORING_PASSWORD", "test-monitoring-password-1234")));
            context.register(SecurityConfigTest.TestConfig.class, SecurityConfig.class, MonitoringSecurityConfiguration.class);
            context.refresh();
            var client = WebTestClient.bindToApplicationContext(context).configureClient().build();
            client.get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
            client.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("monitoring", "wrong"))
                    .exchange().expectStatus().isUnauthorized();
            client.get().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("monitoring", "test-monitoring-password-1234"))
                    .exchange().expectStatus().isOk();
            client.post().uri("/actuator/prometheus").headers(h -> h.setBasicAuth("monitoring", "test-monitoring-password-1234"))
                    .exchange().expectStatus().isForbidden();
            client.get().uri("/api/members/me").headers(h -> h.setBasicAuth("monitoring", "test-monitoring-password-1234"))
                    .exchange().expectStatus().isUnauthorized();
        }
    }
}
