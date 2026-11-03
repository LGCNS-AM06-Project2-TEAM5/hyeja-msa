package com.hyeja.config;

import com.hyeja.config.security.ConfigSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class ConfigSecurityStartupTest {
    // Constructor validation is also exercised independently of servlet auto-configuration.
    @Test void missingWeakAndReusedBootstrapSecretsAreRejected() {
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withBean(ConfigSecurityConfiguration.class);
        runner.run(context -> assertThat(context).hasFailed());
        assertThatThrownBy(() -> config(null, "reader-password-1234", "admin-password-5678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config("test-config-key-with-at-least-32-bytes", "", "admin-password-5678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config("short", "reader-password-1234", "admin-password-5678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config("test-config-key-with-at-least-32-bytes", "short", "admin-password-5678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config("test-config-key-with-at-least-32-bytes", "same-password-1234", "same-password-1234"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config("same-key-and-password-with-32-bytes", "same-key-and-password-with-32-bytes", "admin-password-5678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigSecurityConfiguration("test-config-key-with-at-least-32-bytes",
                "same-user", "reader-password-1234", "same-user", "admin-password-5678"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> config("test-config-key-with-at-least-32-bytes", "reader-password-1234", "admin-password-5678"))
                .doesNotThrowAnyException();
    }

    ConfigSecurityConfiguration config(String key, String readerPassword, String adminPassword) {
        return new ConfigSecurityConfiguration(key, "reader", readerPassword, "admin", adminPassword);
    }
}
