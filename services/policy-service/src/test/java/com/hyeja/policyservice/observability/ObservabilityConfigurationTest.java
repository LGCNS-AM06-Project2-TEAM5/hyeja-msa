package com.hyeja.policyservice.observability;

import static org.assertj.core.api.Assertions.assertThat;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

class ObservabilityConfigurationTest {
    /** Validates every independent application's YAML and Compose inheritance to catch partial rollouts. */
    @Test
    void allFiveServicesHaveCorrelatedLogsProtectedScrapesAndPersistentStorage() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var compose = loader.load("compose", new FileSystemResource("../../compose.yaml")).get(0);
        for (String service : new String[]{"gateway", "user", "policy", "engagement", "recommendation"}) {
            String prefix = "services." + service + "-service.";
            assertThat(compose.getProperty(prefix + "environment.MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE"))
                    .isEqualTo("health,info,prometheus");
            assertThat(compose.getProperty(prefix + "environment.MONITORING_PASSWORD").toString())
                    .contains("MONITORING_PASSWORD:?");
            assertThat(compose.getProperty(prefix + "volumes[1]")).isEqualTo(service + "-logs:/app/logs");
            String module = service.equals("gateway") ? "infra" : "services";
            var sources = loader.load(service, new FileSystemResource(Path.of("../..", module,
                    service + "-service", "src/main/resources/application.yml")));
            assertThat(sources).anySatisfy(source -> {
                assertThat(String.valueOf(source.getProperty("logging.pattern.correlation"))).contains("%X{traceId:-}", "%X{spanId:-}");
                assertThat(source.getProperty("management.metrics.distribution.percentiles-histogram.http.server.requests")).isEqualTo(true);
            });
        }
        assertThat(compose.getProperty("services.config-service.environment.MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE"))
                .isEqualTo("health,info,busrefresh");
        assertThat(compose.getProperty("services.config-service.environment.CONFIG_ALLOWED_PROFILES").toString()).contains("debug");
        assertThat(compose.getProperty("services.prometheus.ports[0]").toString()).startsWith("127.0.0.1:");
        var prometheus = loader.load("prometheus", new FileSystemResource("../../docker/prometheus/prometheus.yml")).get(0);
        assertThat(prometheus.getProperty("scrape_configs[0].basic_auth.password_file")).isEqualTo("/run/secrets/monitoring_password");
        assertThat(prometheus.getProperty("scrape_configs[0].static_configs[0].targets[4]")).isEqualTo("recommendation-service:8084");
        loader.load("rules", new FileSystemResource("../../docker/prometheus/recording-rules.yml"));
    }

    @Test
    void exportedSpansDoNotContainRawUrlsButKeepRouteAndTimingContext() {
        var context = new Observation.Context();
        context.addHighCardinalityKeyValue(KeyValue.of("http.url", "https://example.test?key=private"));
        context.addHighCardinalityKeyValue(KeyValue.of("url.full", "https://example.test?key=private"));
        context.addLowCardinalityKeyValue(KeyValue.of("uri", "/api/policies/{policyId}"));
        var filtered = new TracePrivacyConfiguration().redactHttpUrls().map(context);
        assertThat(filtered.getHighCardinalityKeyValue("http.url")).isNull();
        assertThat(filtered.getHighCardinalityKeyValue("url.full")).isNull();
        assertThat(filtered.getLowCardinalityKeyValue("uri").getValue()).isEqualTo("/api/policies/{policyId}");
    }
}
