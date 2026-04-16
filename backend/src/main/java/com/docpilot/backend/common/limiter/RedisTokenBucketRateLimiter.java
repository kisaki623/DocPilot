package com.docpilot.backend.common.limiter;

import com.docpilot.backend.common.util.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class RedisTokenBucketRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    private static final DefaultRedisScript<List> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();

    static {
        TOKEN_BUCKET_SCRIPT.setScriptText(
                "local key = KEYS[1]\n"
                        + "local now = tonumber(ARGV[1])\n"
                        + "local capacity = tonumber(ARGV[2])\n"
                        + "local refillTokens = tonumber(ARGV[3])\n"
                        + "local refillIntervalMs = tonumber(ARGV[4])\n"
                        + "local requestedTokens = tonumber(ARGV[5])\n"
                        + "local state = redis.call('HMGET', key, 'tokens', 'ts')\n"
                        + "local tokens = tonumber(state[1])\n"
                        + "local ts = tonumber(state[2])\n"
                        + "if tokens == nil then tokens = capacity end\n"
                        + "if ts == nil then ts = now end\n"
                        + "if now > ts then\n"
                        + "  local elapsed = now - ts\n"
                        + "  local refillCycles = math.floor(elapsed / refillIntervalMs)\n"
                        + "  if refillCycles > 0 then\n"
                        + "    tokens = math.min(capacity, tokens + refillCycles * refillTokens)\n"
                        + "    ts = ts + refillCycles * refillIntervalMs\n"
                        + "  end\n"
                        + "end\n"
                        + "local allowed = 0\n"
                        + "if tokens >= requestedTokens then\n"
                        + "  tokens = tokens - requestedTokens\n"
                        + "  allowed = 1\n"
                        + "end\n"
                        + "redis.call('HMSET', key, 'tokens', tokens, 'ts', ts)\n"
                        + "local ttlSeconds = math.ceil((capacity / refillTokens) * refillIntervalMs / 1000) * 2\n"
                        + "if ttlSeconds < 1 then ttlSeconds = 1 end\n"
                        + "redis.call('EXPIRE', key, ttlSeconds)\n"
                        + "return {allowed, tokens, ts}\n"
        );
        TOKEN_BUCKET_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public RedisTokenBucketRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean tryConsume(String key,
                              int capacity,
                              int refillTokens,
                              long refillIntervalSeconds) {
        return tryConsume(key, capacity, refillTokens, refillIntervalSeconds, 1);
    }

    public boolean tryConsume(String key,
                              int capacity,
                              int refillTokens,
                              long refillIntervalSeconds,
                              int requestedTokens) {
        ValidationUtils.requireNonBlank(key, "key");
        if (capacity <= 0 || refillTokens <= 0 || refillIntervalSeconds <= 0 || requestedTokens <= 0) {
            throw new IllegalArgumentException("令牌桶参数非法");
        }

        try {
            long now = System.currentTimeMillis();
            List<?> result = stringRedisTemplate.execute(
                    TOKEN_BUCKET_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(capacity),
                    String.valueOf(refillTokens),
                    String.valueOf(refillIntervalSeconds * 1000),
                    String.valueOf(requestedTokens)
            );
            if (result == null || result.isEmpty()) {
                return true;
            }
            return toLong(result.get(0), 1L) == 1L;
        } catch (Exception ex) {
            log.warn("令牌桶限流执行失败，降级放行。key={}", key, ex);
            return true;
        }
    }

    private long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
