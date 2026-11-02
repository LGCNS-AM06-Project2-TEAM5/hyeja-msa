package com.hyeja.userservice.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TokenBlacklist tokenBlacklist;

    @BeforeEach
    void setUp() {
        tokenBlacklist = new TokenBlacklist(redisTemplate);
    }

    @Test
    void storesTokenWithGatewayCompatibleKeyUntilExpiration() {
        Duration remaining = Duration.ofMinutes(10);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenBlacklist.add("access-token", remaining);

        verify(valueOperations).set("blacklist:access-token", "logout", remaining);
    }

    @Test
    void ignoresAlreadyExpiredToken() {
        tokenBlacklist.add("expired-token", Duration.ZERO);

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void reportsWhetherTokenIsBlacklisted() {
        when(redisTemplate.hasKey("blacklist:access-token")).thenReturn(true);

        assertThat(tokenBlacklist.contains("access-token")).isTrue();
    }
}
