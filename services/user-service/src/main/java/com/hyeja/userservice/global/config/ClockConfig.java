package com.hyeja.userservice.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 만 나이를 한국 날짜 기준으로 계산하려고 서울 시간대 Clock을 빈으로 설정
// 테스트에서는 Clock.fixed(...)로 바꿔 날짜를 고정할 수 있음
@Configuration
public class ClockConfig {

    @Bean
    public Clock koreaClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
