package com.hyeja.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class TokenBlacklistTest {
    @Test
    void readsExistingUserLogoutKeyWithoutChangingRedis() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.hasKey("blacklist:original.jwt.token")).thenReturn(Mono.just(true));
        assertTrue(new TokenBlacklist(redis).contains("original.jwt.token").block());
        verify(redis).hasKey("blacklist:original.jwt.token");
        verifyNoMoreInteractions(redis);
    }
}
