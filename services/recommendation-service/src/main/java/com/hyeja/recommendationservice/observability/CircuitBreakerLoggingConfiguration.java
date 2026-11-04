package com.hyeja.recommendationservice.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Logs circuit breaker state transitions by breaker name only; no member IDs or request bodies. */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class CircuitBreakerLoggingConfiguration {

    @Bean
    RegistryEventConsumer<CircuitBreaker> circuitBreakerTransitionLogger() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                event.getAddedEntry().getEventPublisher().onStateTransition(transition ->
                        log.warn("Circuit breaker state changed. name={}, transition={}",
                                transition.getCircuitBreakerName(), transition.getStateTransition()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
            }
        };
    }
}
