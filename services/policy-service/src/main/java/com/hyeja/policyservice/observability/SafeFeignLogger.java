package com.hyeja.policyservice.observability;

import feign.Logger;
import feign.Request;
import feign.Response;
import java.io.IOException;
import org.slf4j.LoggerFactory;

/**
 * Preserves Feign timing/status diagnostics even at FULL level, while never
 * recording URLs, credentials, headers or request/response bodies.
 */
public class SafeFeignLogger extends Logger {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SafeFeignLogger.class);

    /** Records the client method and HTTP verb; arguments and transport content remain private. */
    @Override
    protected void logRequest(String configKey, Level level, Request request) {
        log(configKey, "---> %s [URL, headers and body redacted]", request.httpMethod());
    }

    /** Does not consume the body, so application deserialization receives the original response. */
    @Override
    protected Response logAndRebufferResponse(String configKey, Level level, Response response, long elapsedTime)
            throws IOException {
        log(configKey, "<--- status=%s durationMs=%s [headers and body redacted]", response.status(), elapsedTime);
        return response;
    }

    /** Logs only the exception type; exception messages can contain URLs and sensitive parameters. */
    @Override
    protected IOException logIOException(String configKey, Level level, IOException error, long elapsedTime) {
        log(configKey, "<--- error=%s durationMs=%s", error.getClass().getSimpleName(), elapsedTime);
        return error;
    }

    /** Delegates sanitized messages to SLF4J, retaining MDC trace/span correlation. */
    @Override
    protected void log(String configKey, String format, Object... args) {
        LOG.debug("{} {}", methodTag(configKey), String.format(format, args));
    }
}
