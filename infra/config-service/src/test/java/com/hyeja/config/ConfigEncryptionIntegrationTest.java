package com.hyeja.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.config.server.health.enabled=false",
        "encrypt.key=test-only-config-encryption-key-at-least-32-bytes",
        "config-security.reader-username=test-reader", "config-security.reader-password=test-reader-password-1234",
        "config-security.admin-username=test-admin", "config-security.admin-password=test-admin-password-5678"
})
@ActiveProfiles({"native", "test"})
class ConfigEncryptionIntegrationTest {
    @TempDir static Path repository;
    private static final String VALUE = "test-only-internal-jwt-key-at-least-32-bytes";
    private static final String READER_PASSWORD = "test-reader-password-1234";
    private static final String ADMIN_PASSWORD = "test-admin-password-5678";
    @LocalServerPort int port;
    @Autowired WebApplicationContext context;
    @Autowired TextEncryptor encryptor;
    MockMvc mvc;

    @DynamicPropertySource
    static void repository(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.config.server.native.search-locations", () -> repository.toUri().toString());
    }

    @BeforeEach void prepare() throws Exception {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        writeSettings("{cipher}" + encryptor.encrypt(VALUE));
    }

    void writeSettings(String value) throws Exception {
        Files.writeString(repository.resolve("crypto-probe-docker.yml"),
                "internal-auth:\n  secret: '" + value + "'\n");
    }

    @Test void onlyAdminCanEncryptAndDecryptIncludingScopedEndpoints() throws Exception {
        String cipher = mvc.perform(post("/encrypt").with(httpBasic("test-admin", ADMIN_PASSWORD))
                        .contentType(MediaType.TEXT_PLAIN).content(VALUE + "+=&한글"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(cipher).isNotBlank().doesNotContain(VALUE);
        mvc.perform(post("/decrypt").with(httpBasic("test-admin", ADMIN_PASSWORD))
                        .contentType(MediaType.TEXT_PLAIN).content(cipher))
                .andExpect(status().isOk()).andExpect(content().string(VALUE + "+=&한글"));
        for (String path : new String[]{"/encrypt", "/decrypt", "/encrypt/crypto-probe/docker", "/decrypt/crypto-probe/docker"}) {
            mvc.perform(post(path).contentType(MediaType.TEXT_PLAIN).content("not-secret"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).with(httpBasic("test-reader", READER_PASSWORD))
                            .contentType(MediaType.TEXT_PLAIN).content("not-secret"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/encrypt/status").with(httpBasic("test-reader", READER_PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/encrypt/status").with(httpBasic("test-admin", ADMIN_PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(post("/decrypt").with(httpBasic("test-admin", ADMIN_PASSWORD))
                        .contentType(MediaType.TEXT_PLAIN).content("not-valid-ciphertext"))
                .andExpect(status().isBadRequest());
    }

    @Test void decryptedConfigurationRequiresReaderCredentials() throws Exception {
        mvc.perform(get("/crypto-probe/docker")).andExpect(status().isUnauthorized());
        mvc.perform(get("/crypto-probe/docker").with(httpBasic("test-reader", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/crypto-probe/docker").with(httpBasic("test-reader", READER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertySources[0].source['internal-auth.secret']").value(VALUE));
        mvc.perform(get("/crypto-probe-docker.yml")).andExpect(status().isUnauthorized());
        mvc.perform(get("/crypto-probe-docker.yml").with(httpBasic("test-reader", READER_PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(VALUE)));
    }

    @Test void corruptCipherRejectsWholeEnvironmentInsteadOfFallingBack() throws Exception {
        Files.writeString(repository.resolve("application.yml"), "internal-auth:\n  secret: fallback-test-secret\n");
        writeSettings("{cipher}not-valid-ciphertext");
        for (String path : new String[]{"/crypto-probe/docker", "/crypto-probe-docker.yml", "/crypto-probe-docker.properties"}) {
            mvc.perform(get(path).with(httpBasic("test-reader", READER_PASSWORD)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fallback-test-secret"))));
        }
        assertThatThrownBy(() -> client(null).run("--spring.cloud.config.enabled=true"))
                .hasStackTraceContaining("Could not locate PropertySource");
        Files.deleteIfExists(repository.resolve("application.yml"));
    }

    @Test void authenticatedConfigClientLoadsCipherAndEnvironmentOverrideIsExplicit() {
        try (var loaded = client(null).run("--spring.cloud.config.enabled=true")) {
            assertThat(loaded.getEnvironment().getProperty("internal-auth.secret")).isEqualTo(VALUE);
        }
        try (var overridden = client("explicit-environment-test-value").run("--spring.cloud.config.enabled=true")) {
            assertThat(overridden.getEnvironment().getProperty("internal-auth.secret"))
                    .isEqualTo("explicit-environment-test-value");
        }
    }

    @Test void configClientCannotLoadSecretsWithIncorrectCredentials() {
        assertThatThrownBy(() -> client(null).run("--spring.cloud.config.enabled=true",
                        "--spring.cloud.config.password=wrong-password"))
                .hasStackTraceContaining("Could not locate PropertySource")
                .hasStackTraceContaining("401");
    }

    SpringApplication client(String override) {
        var app = new SpringApplication(ClientProbe.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setRegisterShutdownHook(false);
        var env = new StandardEnvironment();
        env.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource("systemEnvironment", override == null ? Map.of()
                        : Map.of("INTERNAL_AUTH_SECRET", override)));
        app.setEnvironment(env);
        app.setDefaultProperties(Map.of(
                "spring.application.name", "crypto-probe",
                "spring.profiles.active", "docker",
                "spring.config.location", "optional:classpath:/nonexistent-client-probe.yml",
                "spring.config.import", "configserver:http://localhost:" + port,
                "spring.cloud.config.username", "test-reader",
                "spring.cloud.config.password", READER_PASSWORD));
        return app;
    }

    static class ClientProbe {}

    @Test void healthRemainsPublicButOtherActuatorAndWritesAreDenied() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").with(httpBasic("test-admin", ADMIN_PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/actuator/refresh").with(httpBasic("test-admin", ADMIN_PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/monitor").with(httpBasic("test-reader", READER_PASSWORD)))
                .andExpect(status().isForbidden());
    }
}
