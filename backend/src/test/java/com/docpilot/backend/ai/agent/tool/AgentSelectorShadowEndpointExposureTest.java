package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = AgentSelectorShadowEndpointExposureTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration",
                "app.agent.selector.llm-provider=disabled",
                "app.agent.selector.llm-model=",
                "app.agent.selector.llm-base-url=",
                "app.agent.selector.llm-api-key=",
                "app.agent.selector.shadow-enabled=false",
                "app.agent.selector.real-shadow-enabled=false",
                "app.agent.selector.real-shadow-record-metrics=false"
        }
)
class AgentSelectorShadowEndpointExposureTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldNotExposeEndpointByDefault() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/agentSelectorShadow", String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            AgentSelectorShadowEndpoint.class,
            SelectorMetricsCollector.class,
            SelectorMetricsDebugReporter.class
    })
    static class TestApplication {
    }
}
