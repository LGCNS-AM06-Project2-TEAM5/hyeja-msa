package com.hyeja.engagementservice.security;

import com.hyeja.engagementservice.global.security.GatewayTokenVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class InternalKeyConfigurationTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(GatewayTokenVerifier.class);

    @Test void missingOrWeakKeyPreventsStartup() {
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("internal-auth.secret=").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("internal-auth.secret=short").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("internal-auth.secret=" + InternalTokenFixture.SECRET)
                .run(context -> assertThat(context).hasNotFailed());
    }
}
