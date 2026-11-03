package com.hyeja.recommendationservice.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class RefreshStatusEndpointTest {
    @Test
    void probeChangesOnlyAfterRefreshScopeIsInvalidated() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var values = new HashMap<String, Object>(Map.of(
                    "spring.application.name", "recommendation-service",
                    "hyeja.refresh.message", "before-refresh"));
            context.getEnvironment().setActiveProfiles("bus-test");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", values));
            context.registerBean(RefreshScope.class);
            context.register(RefreshStatusEndpoint.class, RefreshStatusEndpoint.RefreshProbe.class);
            context.refresh();

            var endpoint = context.getBean(RefreshStatusEndpoint.class);
            assertEquals("before-refresh", endpoint.status().get("message"));
            values.put("hyeja.refresh.message", "after-refresh");
            assertEquals("before-refresh", endpoint.status().get("message"));
            context.getBean(RefreshScope.class).refreshAll();
            assertEquals("after-refresh", endpoint.status().get("message"));
        }
    }

    @Test
    void probeDoesNotExistWithoutExplicitTestProfile() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(RefreshStatusEndpoint.class, RefreshStatusEndpoint.RefreshProbe.class);
            context.refresh();
            assertFalse(context.containsBeanDefinition("refreshStatusEndpoint"));
            assertEquals(0, context.getBeansOfType(RefreshStatusEndpoint.class).size());
        }
    }
}
