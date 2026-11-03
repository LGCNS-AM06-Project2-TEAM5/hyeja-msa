package com.hyeja.config;

import com.hyeja.config.security.ConfigAccessProperties;
import com.hyeja.config.security.ConfigSecurityConfiguration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigSecurityStartupTest {
    ConfigAccessProperties access(String user, String password, String adminPassword) {
        return new ConfigAccessProperties("admin", adminPassword,
                Map.of("gateway-service", new ConfigAccessProperties.Reader(user, password)),
                Set.of("default", "docker"), Set.of("main"));
    }

    @Test void missingWeakReusedSecretsAndPlainHttpAreRejected() {
        var valid = access("gateway", "reader-password-1234", "admin-password-5678");
        assertThatThrownBy(() -> new ConfigSecurityConfiguration(null, true, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigSecurityConfiguration("short", true, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigSecurityConfiguration("test-key-with-at-least-32-utf8-bytes", false, valid))
                .isInstanceOf(IllegalArgumentException.class);
        for (var invalid : new ConfigAccessProperties[]{
                access("gateway", "", "admin-password-5678"), access("gateway", "short", "admin-password-5678"),
                access("gateway", "same-password-1234", "same-password-1234"),
                access("admin", "reader-password-1234", "admin-password-5678"),
                new ConfigAccessProperties("admin", "admin-password-5678", Map.of(), Set.of("default"), Set.of("main")),
                new ConfigAccessProperties("admin", "admin-password-5678", Map.of(
                        "gateway-service", new ConfigAccessProperties.Reader("same", "reader-password-1234"),
                        "user-service", new ConfigAccessProperties.Reader("same", "user-password-1234")),
                        Set.of("default"), Set.of("main"))}) {
            assertThatThrownBy(() -> new ConfigSecurityConfiguration("test-key-with-at-least-32-utf8-bytes", true, invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatCode(() -> new ConfigSecurityConfiguration("test-key-with-at-least-32-utf8-bytes", true, valid))
                .doesNotThrowAnyException();
    }
}