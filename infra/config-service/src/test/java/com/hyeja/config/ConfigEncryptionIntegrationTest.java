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
        "spring.cloud.bus.enabled=false",
        "spring.cloud.config.server.health.enabled=false",
        "encrypt.key=test-only-config-encryption-key-at-least-32-bytes",
        "config-security.readers.gateway-service.username=config-gateway", "config-security.readers.gateway-service.password=test-gateway-password-1234",
        "config-security.readers.user-service.password=test-user-password-1234",
        "config-security.readers.policy-service.password=test-policy-password-1234",
        "config-security.readers.engagement-service.password=test-engagement-password-1234",
        "config-security.readers.recommendation-service.password=test-recommendation-password-1234",
        "config-security.admin-username=test-admin", "config-security.admin-password=test-admin-password-5678"
})
@ActiveProfiles({"native", "test"})
class ConfigEncryptionIntegrationTest {
    @TempDir static Path repository;
    @TempDir static Path certificates;
    static TlsTestMaterial tls;
    private static final String VALUE = "test-only-internal-jwt-key-at-least-32-bytes";
    private static final String READER_PASSWORD = "test-gateway-password-1234";
    private static final String ADMIN_PASSWORD = "test-admin-password-5678";
    @LocalServerPort int port;
    @Autowired WebApplicationContext context;
    @Autowired TextEncryptor encryptor;
    MockMvc mvc;

    @DynamicPropertySource
    static void repository(DynamicPropertyRegistry registry) throws Exception {
        tls = new TlsTestMaterial(certificates);
        registry.add("server.ssl.key-store", () -> tls.server.toUri().toString());
        registry.add("server.ssl.key-store-password", () -> TlsTestMaterial.PASSWORD);
        registry.add("spring.cloud.config.server.native.search-locations", () -> repository.toUri().toString());
    }

    @BeforeEach void prepare() throws Exception {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        writeSettings("{cipher}" + encryptor.encrypt(VALUE));
        for (String name : new String[]{"user-service", "policy-service", "engagement-service", "recommendation-service"}) {
            Files.writeString(repository.resolve(name + "-docker.yml"), "scope.marker: " + name + "\n");
        }
    }

    void writeSettings(String value) throws Exception {
        Files.writeString(repository.resolve("gateway-service-docker.yml"),
                "scope.marker: gateway-service\ninternal-auth:\n  secret: '" + value + "'\n");
    }

    @Test void onlyAdminCanEncryptAndDecryptIncludingScopedEndpoints() throws Exception {
        String cipher = mvc.perform(post("/encrypt").with(httpBasic("test-admin", ADMIN_PASSWORD))
                        .contentType(MediaType.TEXT_PLAIN).content(VALUE + "+=&한글"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(cipher).isNotBlank().doesNotContain(VALUE);
        mvc.perform(post("/decrypt").with(httpBasic("test-admin", ADMIN_PASSWORD))
                        .contentType(MediaType.TEXT_PLAIN).content(cipher))
                .andExpect(status().isOk()).andExpect(content().string(VALUE + "+=&한글"));
        for (String path : new String[]{"/encrypt", "/decrypt", "/encrypt/gateway-service/docker", "/decrypt/gateway-service/docker"}) {
            mvc.perform(post(path).contentType(MediaType.TEXT_PLAIN).content("not-secret"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).with(httpBasic("config-gateway", READER_PASSWORD))
                            .contentType(MediaType.TEXT_PLAIN).content("not-secret"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/encrypt/status").with(httpBasic("config-gateway", READER_PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/encrypt/status").with(httpBasic("test-admin", ADMIN_PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(post("/decrypt").with(httpBasic("test-admin", ADMIN_PASSWORD))
                        .contentType(MediaType.TEXT_PLAIN).content("not-valid-ciphertext"))
                .andExpect(status().isBadRequest());
    }

    @Test void decryptedConfigurationRequiresReaderCredentials() throws Exception {
        mvc.perform(get("/gateway-service/docker")).andExpect(status().isUnauthorized());
        mvc.perform(get("/gateway-service/docker").with(httpBasic("config-gateway", "wrong-password")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/gateway-service/docker").with(httpBasic("config-gateway", READER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertySources[0].source['internal-auth.secret']").value(VALUE));
        mvc.perform(get("/gateway-service-docker.yml")).andExpect(status().isUnauthorized());
        mvc.perform(get("/gateway-service-docker.yml").with(httpBasic("config-gateway", READER_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test void corruptCipherRejectsWholeEnvironmentInsteadOfFallingBack() throws Exception {
        Files.writeString(repository.resolve("application.yml"), "internal-auth:\n  secret: fallback-test-secret\n");
        writeSettings("{cipher}not-valid-ciphertext");
        for (String path : new String[]{"/gateway-service/docker"}) {
            mvc.perform(get(path).with(httpBasic("config-gateway", READER_PASSWORD)))
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
        return client(override, true, "localhost");
    }

    SpringApplication client(String override, boolean trusted, String host) {
        return client(override, trusted, host, "gateway-service", "config-gateway", READER_PASSWORD);
    }

    SpringApplication client(String override, boolean trusted, String host, String application,
                             String username, String password) {
        var app = new SpringApplication(ClientProbe.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setRegisterShutdownHook(false);
        var env = new StandardEnvironment();
        env.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource("systemEnvironment", override == null ? Map.of()
                        : Map.of("INTERNAL_AUTH_SECRET", override)));
        app.setEnvironment(env);
        var settings = new java.util.HashMap<String, Object>(Map.of(
                "spring.application.name", application,
                "spring.profiles.active", "docker",
                "spring.config.location", "optional:classpath:/nonexistent-client-probe.yml",
                "spring.config.import", "configserver:https://" + host + ":" + port,
                "spring.cloud.config.name", application,
                "spring.cloud.config.username", username,
                "spring.cloud.config.password", password));
        settings.put("spring.cloud.config.tls.enabled", true);
        if (trusted) {
            settings.put("spring.cloud.config.tls.trust-store", tls.trust.toUri().toString());
            settings.put("spring.cloud.config.tls.trust-store-password", TlsTestMaterial.PASSWORD);
            settings.put("spring.cloud.config.tls.trust-store-type", "PKCS12");
        }
        app.setDefaultProperties(settings);
        return app;
    }

    @Test void untrustedCertificatesAndWrongHostnamesAreRejected() {
        assertThatThrownBy(() -> client(null, false, "localhost").run("--spring.cloud.config.enabled=true"))
                .hasStackTraceContaining("SSLHandshakeException");
        assertThatThrownBy(() -> client(null, true, "127.0.0.1").run("--spring.cloud.config.enabled=true"))
                .hasStackTraceContaining("SSLPeerUnverifiedException");
    }

    @Test void tlsPortDoesNotServeConfigurationOverPlainHttp() throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "http://localhost:" + port + "/gateway-service/docker")).timeout(java.time.Duration.ofSeconds(5)).build();
        try {
            var response = java.net.http.HttpClient.newHttpClient().send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isGreaterThanOrEqualTo(400);
            assertThat(response.body()).doesNotContain(VALUE);
        } catch (java.io.IOException closedTlsConnection) {
            // A TLS connector may close an HTTP connection instead of returning a 400 response.
        }
    }

    @Test void allFiveConfigClientsLoadTheirOwnConfigurationOverVerifiedHttps() {
        for (String service : new String[]{"gateway", "user", "policy", "engagement", "recommendation"}) {
            String application = service + "-service";
            try (var loaded = client(null, true, "localhost", application, "config-" + service,
                    "test-" + service + "-password-1234").run("--spring.cloud.config.enabled=true")) {
                assertThat(loaded.getEnvironment().getProperty("scope.marker")).isEqualTo(application);
            }
        }
    }

    @Test void applicationAccountsCannotReadOtherServicesOrAlternateFormats() throws Exception {
        var applications = Map.of("gateway-service", "gateway", "user-service", "user",
                "policy-service", "policy", "engagement-service", "engagement",
                "recommendation-service", "recommendation");
        for (var own : applications.entrySet()) {
            var basic = httpBasic("config-" + own.getValue(), "test-" + own.getValue() + "-password-1234");
            mvc.perform(get("/" + own.getKey() + "/docker").with(basic)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.propertySources[0].source['scope.marker']").value(own.getKey()));
            mvc.perform(get("/" + own.getKey() + "/default,docker").with(basic)).andExpect(status().isOk());
            for (String target : applications.keySet()) {
                if (!target.equals(own.getKey())) {
                    mvc.perform(get("/" + target + "/docker").with(basic)).andExpect(status().isForbidden());
                }
            }
            for (String path : new String[]{"/application/default", "/" + own.getKey() + ",application/docker",
                    "/" + own.getKey() + "/docker/unapproved-label", "/" + own.getKey() + "/prod",
                    "/" + own.getKey() + "-docker.yml", "/" + own.getKey() + "-docker.json",
                    "/" + own.getKey() + "-docker.properties", "/main/" + own.getKey() + "-docker.yaml",
                    "/" + own.getKey() + "/docker/main/user-service.yml"}) {
                mvc.perform(get(path).with(basic)).andExpect(status().isForbidden());
            }
        }
        mvc.perform(get("/gateway-service/docker/main").with(httpBasic("config-gateway", READER_PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/user%2Dservice/docker").with(httpBasic("config-gateway", READER_PASSWORD)))
                .andExpect(status().is4xxClientError());
    }

    static class ClientProbe {}

    @Test void healthRemainsPublicButOtherActuatorAndWritesAreDenied() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").with(httpBasic("test-admin", ADMIN_PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/actuator/refresh").with(httpBasic("test-admin", ADMIN_PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/monitor").with(httpBasic("config-gateway", READER_PASSWORD)))
                .andExpect(status().isForbidden());
    }
}
