package com.hyeja.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class InternalAuthConfigurationTest {
    private static final String USER_KEY = "test-only-user-key-at-least-32-bytes-long";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(EngagementTokenIssuer.class)
            .withPropertyValues("jwt.secret=" + USER_KEY);

    @Test void missingBlankWeakOrReusedKeyPreventsStartup() {
        runner.run(context -> assertThat(context).hasFailed());
        for (String key : new String[]{"", "short", USER_KEY}) {
            runner.withPropertyValues("internal-auth.secret=" + key)
                    .run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("internal-auth.secret=test-only-internal-key-at-least-32-bytes-long")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
