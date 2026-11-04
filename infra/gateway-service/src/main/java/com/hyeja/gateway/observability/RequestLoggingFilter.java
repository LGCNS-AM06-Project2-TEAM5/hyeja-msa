package com.hyeja.gateway.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Emits request summaries inside the Reactor tracing context, without raw URLs or payloads. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter implements WebFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ObjectProvider<Tracer> tracers;

    public RequestLoggingFilter(ObjectProvider<Tracer> tracers) {
        this.tracers = tracers;
    }

    /** Logs completion and errors once; Reactor automatic context propagation restores MDC. */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            var tracer = tracers.getIfAvailable();
            var span = tracer == null ? null : tracer.currentSpan();
            if (span != null) {
                String traceId = span.context().traceId();
                exchange.getResponse().beforeCommit(() -> Mono.fromRunnable(() ->
                        exchange.getResponse().getHeaders().set("X-Trace-Id", traceId)));
            }
            long started = System.nanoTime();
            return chain.filter(exchange).doOnEach(signal -> {
                if (signal.isOnComplete() || signal.isOnError()) {
                    Object route = exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                    // Gateway routes use Path predicates rather than controller mapping patterns.
                    Object gatewayRoute = exchange.getAttribute(
                            org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    String target = gatewayRoute instanceof org.springframework.cloud.gateway.route.Route value
                            ? value.getId() : String.valueOf(route == null ? "<unmatched>" : route);
                    int status = exchange.getResponse().getStatusCode() == null
                            ? 200 : exchange.getResponse().getStatusCode().value();
                    LOG.info("HTTP method={} route={} status={} outcome={} durationMs={}",
                            exchange.getRequest().getMethod(), target, status,
                            signal.isOnError() ? "ERROR" : "COMPLETE",
                            (System.nanoTime() - started) / 1_000_000);
                }
            });
        });
    }
}
