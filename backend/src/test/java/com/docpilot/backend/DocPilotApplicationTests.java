package com.docpilot.backend;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.main.lazy-initialization=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
        "app.scheduling.enabled=false",
        "app.rocketmq.enabled=false",
        "app.rocketmq.outbox.scan-enabled=false",
        "app.redisson.enabled=false",
        "app.agent.selector.llm-provider=disabled",
        "app.agent.selector.llm-model=",
        "app.agent.selector.llm-base-url=",
        "app.agent.selector.llm-api-key=",
        "app.agent.selector.shadow-enabled=false",
        "app.agent.selector.real-shadow-enabled=false",
        "app.agent.selector.real-shadow-record-metrics=false"
})
class DocPilotApplicationTests {

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }
}

