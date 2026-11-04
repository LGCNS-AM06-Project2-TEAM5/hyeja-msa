package com.hyeja.recommendationservice.favorite.client;

import com.hyeja.recommendationservice.favorite.dto.FavoriteActivePolicyIdsRequest;
import com.hyeja.recommendationservice.global.apiPayload.ApiResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// F3 (recommendation → engagement). 주소는 Eureka 서비스 ID로 LoadBalancer가 풀고, Timeout은 application.yml의 default 설정을 씁니다.
// engagement는 인증 없는 요청을 401로 막으므로, 서비스 간 인증 계약이 확정되면 이 메서드의 @RequestHeader 또는
// engagement-service Client 설정의 RequestInterceptor로 인증 헤더를 추가해야 실제로 연동됩니다.
@FeignClient(name = "engagement-service")
public interface FavoriteFeignClient {

    @PostMapping("/internal/favorites/active-policy-ids")
    ApiResponse<List<String>> findActivePolicyIds(@RequestBody FavoriteActivePolicyIdsRequest request);
}
