package com.hyeja.recommendationservice.observability;

import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Enables opt-in Feign diagnostics, with sanitization retained for every profile. */
@Configuration(proxyBeanMethods = false)
public class FeignLoggingConfiguration {
    /** Uses FULL only in debug; the safe logger still suppresses sensitive transport data. */
    @Bean
    @Profile("debug")
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /** Also protects against FULL being enabled through remote configuration. */
    @Bean
    public Logger feignLogger() {
        return new SafeFeignLogger();
    }
}
