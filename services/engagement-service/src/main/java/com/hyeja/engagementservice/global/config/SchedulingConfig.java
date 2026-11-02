package com.hyeja.engagementservice.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    public static final String KOREA_TIME_ZONE = "Asia/Seoul";

    @Bean
    public Clock koreaClock() {
        return Clock.system(ZoneId.of(KOREA_TIME_ZONE));
    }
}
