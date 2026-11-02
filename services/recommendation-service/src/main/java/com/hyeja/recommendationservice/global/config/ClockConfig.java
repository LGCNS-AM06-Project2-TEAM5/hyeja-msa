package com.hyeja.recommendationservice.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 나이 계산의 "오늘"을 한국 날짜로 맞추는 기준 시각입니다(1차 SchedulingConfig의 koreaClock).
@Configuration
public class ClockConfig {

    public static final String KOREA_TIME_ZONE = "Asia/Seoul";

    @Bean
    public Clock koreaClock() {
        return Clock.system(ZoneId.of(KOREA_TIME_ZONE));
    }
}
