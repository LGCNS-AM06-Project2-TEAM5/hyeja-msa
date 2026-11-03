package com.hyeja.recommendationservice.global.config;

import com.hyeja.recommendationservice.RecommendationServiceApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

// 메인 클래스가 아닌 별도 설정에 두어 @WebMvcTest 같은 슬라이스 테스트가 Feign Client 빈을 만들지 않게 합니다.
// Timeout·ErrorDecoder는 Client 이름(Eureka 서비스 ID)별로 application.yml에서 정합니다.
@Configuration
@EnableFeignClients(basePackageClasses = RecommendationServiceApplication.class)
public class FeignConfig {
}
