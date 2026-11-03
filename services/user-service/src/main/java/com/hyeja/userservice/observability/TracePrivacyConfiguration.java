package com.hyeja.userservice.observability;

import io.micrometer.observation.ObservationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keeps raw URLs and query parameters out of exported HTTP span attributes. */
@Configuration(proxyBeanMethods = false)
public class TracePrivacyConfiguration {
    /** Retains route templates and timing while removing potentially secret-bearing URL tags. */
    @Bean
    ObservationFilter redactHttpUrls() {
        return context -> {
            context.removeHighCardinalityKeyValue("http.url");
            context.removeHighCardinalityKeyValue("http.target");
            context.removeHighCardinalityKeyValue("url.full");
            return context;
        };
    }
}
