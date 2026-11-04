package com.hyeja.config;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import static org.assertj.core.api.Assertions.*;

class ComposeConfigSecurityTest {
    @Test void composeBoundsJvmHeapAndContainerMemoryForLocalStack() throws Exception {
        var compose = new YamlPropertySourceLoader()
                .load("compose", new FileSystemResource(Path.of("../../compose.yaml"))).get(0);

        for (String app : new String[]{"discovery", "config", "gateway", "user", "policy", "engagement", "recommendation"}) {
            String prefix = "services." + app + "-service.";
            assertThat(compose.getProperty(prefix + "environment.JAVA_TOOL_OPTIONS"))
                    .isEqualTo("-Xms128m -Xmx256m");
            assertThat(compose.getProperty(prefix + "deploy.resources.limits.memory"))
                    .isEqualTo("384M");
        }

        assertThat(compose.getProperty("services.kafka.environment.KAFKA_HEAP_OPTS"))
                .isEqualTo("-Xms256m -Xmx384m");
        assertThat(compose.getProperty("services.kafka.deploy.resources.limits.memory"))
                .isEqualTo("768M");
        assertThat(compose.getProperty("services.mariadb.deploy.resources.limits.memory"))
                .isEqualTo("512M");
        assertThat(compose.getProperty("services.redis.deploy.resources.limits.memory"))
                .isEqualTo("${REDIS_MEMORY_LIMIT:-128M}");
        assertThat(compose.getProperty("services.redis.command[4]"))
                .isEqualTo("${REDIS_MAXMEMORY:-96mb}");
        assertThat(compose.getProperty("services.redis.command[6]"))
                .isEqualTo("${REDIS_MAXMEMORY_POLICY:-noeviction}");
        assertThat(compose.getProperty("services.zipkin.deploy.resources.limits.memory"))
                .isEqualTo("384M");
        assertThat(compose.getProperty("services.prometheus.deploy.resources.limits.memory"))
                .isEqualTo("256M");
    }

    @Test void busAndHttpsClientSettingsCoexistAfterMerge() throws Exception {
        var loader = new YamlPropertySourceLoader();
        for (String module : new String[]{"../../infra/gateway-service", "../../services/user-service",
                "../../services/policy-service", "../../services/engagement-service", "../../services/recommendation-service"}) {
            var source = loader.load(module, new FileSystemResource(Path.of(module, "src/main/resources/application.yml"))).get(0);
            assertThat(source.getProperty("spring.cloud.bus.destination")).isEqualTo("hyeja-config-bus");
            assertThat(source.getProperty("spring.cloud.config.uri")).isEqualTo("${CONFIG_SERVER_URL:https://localhost:8888}");
        }
        var compose = loader.load("compose", new FileSystemResource(Path.of("../../compose.yaml"))).get(0);
        assertThat(compose.getProperty("services.config-service.environment.MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE"))
                .isEqualTo("health,info,busrefresh");
        assertThat(compose.getProperty("services.gateway-service.depends_on.kafka-init.condition"))
                .isEqualTo("service_completed_successfully");
        var probe = loader.load("bus-test", new FileSystemResource(Path.of("../../compose.bus-test.yaml"))).get(0);
        assertThat(probe.getProperty("services.config-service.environment.CONFIG_ALLOWED_PROFILES").toString())
                .contains("bus-test");
    }

    @Test void composeUsesVerifiedHttpsAndDistinctAccountsWithoutDistributingPrivateKeys() throws Exception {
        var loaded = new YamlPropertySourceLoader().load("compose", new FileSystemResource(Path.of("../../compose.yaml")));
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        ((Map<?, ?>) loaded.get(0).getSource()).keySet().forEach(key ->
                properties.put(key.toString(), loaded.get(0).getProperty(key.toString())));
        for (String app : new String[]{"gateway", "user", "policy", "engagement", "recommendation"}) {
            String prefix = "services." + app + "-service.";
            String env = prefix + "environment.";
            assertThat(properties.get(env + "SPRING_CONFIG_IMPORT")).isEqualTo("configserver:https://config-service:8888");
            assertThat(properties.get(env + "SPRING_CLOUD_CONFIG_URI")).isEqualTo("https://config-service:8888");
            assertThat(properties.get(env + "SPRING_CLOUD_CONFIG_NAME")).isEqualTo(app + "-service");
            assertThat(properties.get(env + "SPRING_CLOUD_CONFIG_USERNAME").toString()).contains("CONFIG_" + app.toUpperCase() + "_USERNAME");
            assertThat(properties.get(env + "SPRING_CLOUD_CONFIG_PASSWORD").toString()).contains("CONFIG_" + app.toUpperCase() + "_PASSWORD");
            assertThat(properties.get(env + "SPRING_CLOUD_CONFIG_TLS_ENABLED").toString()).isEqualTo("true");
            assertThat(properties.get(env + "SPRING_CLOUD_CONFIG_TLS_TRUST_STORE")).isEqualTo("file:/run/config-trust/truststore.p12");
            assertThat(properties.get(prefix + "volumes[0]").toString()).contains("/trust:").doesNotContain("/private", "/server:");
            assertThat(properties).doesNotContainKeys(env + "CONFIG_ENCRYPT_KEY", env + "CONFIG_ADMIN_PASSWORD");
        }
        assertThat(properties.get("services.config-service.healthcheck.test[5]")).isEqualTo("/run/config-ca/ca.crt");
        assertThat(properties.get("services.config-service.healthcheck.test[6]")).isEqualTo("https://localhost:8888/actuator/health");
    }
}
