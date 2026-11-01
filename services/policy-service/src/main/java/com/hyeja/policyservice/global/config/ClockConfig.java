package com.hyeja.policyservice.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 마감일·신청 기간 비교에 쓰는 기준 시각입니다(1차 SchedulingConfig의 koreaClock).
@Configuration
public class ClockConfig {

    public static final String KOREA_TIME_ZONE = "Asia/Seoul";

    @Bean
    public Clock koreaClock() {
        return Clock.system(ZoneId.of(KOREA_TIME_ZONE));
    }
}
