package com.hyeja.config.security;

import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("config-security")
public record ConfigAccessProperties(String adminUsername, String adminPassword,
        Map<String, Reader> readers, Set<String> allowedProfiles, Set<String> allowedLabels) {
    public record Reader(String username, String password) {
        @Override public String toString() { return "Reader[username=" + username + ", password=<redacted>]"; }
    }
    @Override public String toString() {
        return "ConfigAccessProperties[adminUsername=" + adminUsername + ", adminPassword=<redacted>, readers="
                + readers + ", allowedProfiles=" + allowedProfiles + ", allowedLabels=" + allowedLabels + "]";
    }
}
