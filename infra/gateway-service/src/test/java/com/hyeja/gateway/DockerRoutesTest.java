package com.hyeja.gateway;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;

class DockerRoutesTest {
    private List<RouteDefinition> routes(String resource) throws IOException {
        var environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource))) {
            environment.getPropertySources().addFirst(source);
        }
        return Binder.get(environment).bind("spring.cloud.gateway.server.webflux.routes",
                Bindable.listOf(RouteDefinition.class)).orElseThrow(IllegalStateException::new);
    }

    @Test
    void dockerProfilePreservesBusinessRoutesAndAddsFourHealthRoutes() throws IOException {
        var base = routes("application.yml");
        var docker = routes("application-docker.yml");
        assertEquals(base.size() + 4, docker.size());
        for (var route : base) {
            var matching = docker.stream().filter(r -> r.getId().equals(route.getId())).findFirst().orElseThrow();
            assertEquals(route.getUri(), matching.getUri());
            assertEquals(route.getOrder(), matching.getOrder());
            assertEquals(route.getPredicates().toString(), matching.getPredicates().toString());
        }
        for (var name : List.of("user", "policy", "engagement", "recommendation")) {
            var route = docker.stream().filter(r -> r.getId().equals("docker-health-" + name)).findFirst().orElseThrow();
            assertEquals("lb://" + name + "-service", route.getUri().toString());
            assertTrue(route.getPredicates().toString().contains("/_infra/health/" + name));
            assertTrue(route.getFilters().toString().contains("/health-check"));
        }
    }
}
