package com.hyeja.recommendationservice.global.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Non-sensitive refresh probe, available only with the explicit bus-test profile. */
@Component
@Profile("bus-test")
@Endpoint(id = "refreshstatus")
public class RefreshStatusEndpoint {
    private final RefreshProbe probe;
    private final String application;

    public RefreshStatusEndpoint(RefreshProbe probe,
            @Value("${spring.application.name}") String application) {
        this.probe = probe;
        this.application = application;
    }

    @ReadOperation
    public Map<String, String> status() {
        return Map.of("application", application, "message", probe.getMessage());
    }

    @Component
    @Profile("bus-test")
    @RefreshScope
    public static class RefreshProbe {
        private final String message;

        public RefreshProbe(@Value("${hyeja.refresh.message:before-refresh}") String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
