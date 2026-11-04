package com.hyeja.engagementservice.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Logs HTTP outcomes without request headers, bodies, query strings or concrete identifiers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ObjectProvider<Tracer> tracers;

    public RequestLoggingFilter(ObjectProvider<Tracer> tracers) {
        this.tracers = tracers;
    }

    /** Runs after HTTP observation and before authentication so rejected requests are also recorded. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        var tracer = tracers.getIfAvailable();
        var span = tracer == null ? null : tracer.currentSpan();
        if (span != null) response.setHeader("X-Trace-Id", span.context().traceId());
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            LOG.info("HTTP method={} route={} status={} outcome={} durationMs={}",
                    request.getMethod(), route == null ? "<unmatched>" : route, response.getStatus(),
                    completed ? "COMPLETE" : "ERROR", (System.nanoTime() - started) / 1_000_000);
        }
    }
}
