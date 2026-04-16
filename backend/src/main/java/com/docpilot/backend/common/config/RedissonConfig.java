package com.docpilot.backend.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties,
                                         @Value("${app.redisson.lock-watchdog-timeout-ms:30000}") long lockWatchdogTimeoutMs) {
        Config config = new Config();
        config.setLockWatchdogTimeout(Math.max(30000L, lockWatchdogTimeoutMs));

        boolean sslEnabled = redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled();
        String protocol = sslEnabled ? "rediss://" : "redis://";
        String host = (redisProperties.getHost() == null || redisProperties.getHost().isBlank())
                ? "127.0.0.1"
                : redisProperties.getHost();
        int port = redisProperties.getPort() > 0 ? redisProperties.getPort() : 6379;
        String address = protocol + host + ":" + port;

        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisProperties.getDatabase());

        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }

        Duration timeout = redisProperties.getTimeout();
        if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
            singleServerConfig.setTimeout(Math.toIntExact(timeout.toMillis()));
        }

        return Redisson.create(config);
    }
}


