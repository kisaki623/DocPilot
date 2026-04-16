package com.docpilot.backend.common.limiter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisTokenBucketRateLimiterTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldAllowWhenTokenBucketScriptReturnsAllowed() {
        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(stringRedisTemplate);
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(List.of(1L, 4L, 0L));

        boolean allowed = limiter.tryConsume("docpilot:ratelimit:ai:qa:u:100", 5, 1, 12);

        assertTrue(allowed);
    }

    @Test
    void shouldRejectWhenTokenBucketScriptReturnsDenied() {
        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(stringRedisTemplate);
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(List.of(0L, 0L, 0L));

        boolean allowed = limiter.tryConsume("docpilot:ratelimit:ai:qa:u:100", 5, 1, 12);

        assertFalse(allowed);
    }

    @Test
    void shouldFailOpenWhenRedisExecuteThrows() {
        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(stringRedisTemplate);
        when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenThrow(new RuntimeException("redis down"));

        boolean allowed = limiter.tryConsume("docpilot:ratelimit:ai:qa:u:100", 5, 1, 12);

        assertTrue(allowed);
    }
}
