package com.hyeja.config;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import static org.assertj.core.api.Assertions.*;

class ComposeConfigSecurityTest {
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
