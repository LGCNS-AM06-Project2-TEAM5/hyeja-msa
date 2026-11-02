package com.hyeja.userservice.global.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void add(String token, Duration remaining) {
        if (!remaining.isNegative() && !remaining.isZero()) {
            redisTemplate.opsForValue().set(KEY_PREFIX + token, "logout", remaining);
        }
    }

    public boolean contains(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
