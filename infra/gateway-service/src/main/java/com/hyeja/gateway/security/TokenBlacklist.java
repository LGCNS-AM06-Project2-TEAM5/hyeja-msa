package com.hyeja.gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class TokenBlacklist {
    private final ReactiveStringRedisTemplate redis;

    public TokenBlacklist(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Boolean> contains(String token) {
        // User 서비스의 기존 로그아웃 저장 규칙을 그대로 사용합니다.
        return redis.hasKey("blacklist:" + token);
    }
}
