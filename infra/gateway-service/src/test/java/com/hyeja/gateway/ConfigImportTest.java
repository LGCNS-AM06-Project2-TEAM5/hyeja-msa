package com.hyeja.gateway;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import static org.junit.jupiter.api.Assertions.*;

class ConfigImportTest {
    @Configuration(proxyBeanMethods = false)
    static class ImportOnlyConfiguration {}

    @Test
    void dockerEnvironmentAndFileImportBothUseConfiguredServer() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().getPath());
            byte[] body = ("{\"name\":\"gateway-service\",\"profiles\":[\"default\"],"
                    + "\"propertySources\":[{\"name\":\"test-config\",\"source\":{\"test.config.loaded\":true}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var environment = new StandardEnvironment();
            environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new SystemEnvironmentPropertySource("systemEnvironment", Map.of(
                            "CONFIG_SERVER_URL", url,
                            "SPRING_CONFIG_IMPORT", "configserver:" + url,
                            "SPRING_PROFILES_ACTIVE", "docker")));
            var application = new SpringApplication(ImportOnlyConfiguration.class);
            application.setEnvironment(environment);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setRegisterShutdownHook(false);
            try (var context = application.run()) {
                assertEquals("true", context.getEnvironment().getProperty("test.config.loaded"));
                assertTrue(context.getEnvironment().acceptsProfiles(org.springframework.core.env.Profiles.of("docker")));
                assertTrue(requests.contains("/gateway-service/default"));
                assertTrue(requests.contains("/gateway-service/docker"));
            }
        } finally {
            server.stop(0);
        }
    }
}
