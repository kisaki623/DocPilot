package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = AgentSelectorShadowEndpointEnabledTest.TestApplication.class,
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
                "app.agent.selector.real-shadow-record-metrics=false",
                "management.endpoint.agent-selector-shadow.enabled=true",
                "management.endpoints.web.exposure.include=agentSelectorShadow"
        }
)
class AgentSelectorShadowEndpointEnabledTest {

    private static final String ENDPOINT_PATH = "/actuator/agentSelectorShadow";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SelectorMetricsCollector metricsCollector;

    @Test
    void shouldReturn200WhenExplicitlyEnabled() {
        ResponseEntity<String> response = restTemplate.getForEntity(ENDPOINT_PATH, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void shouldContainOnlyWhitelistedFieldsInResponse() {
        String body = getEndpointBody();

        assertTrue(body.contains("\"totalCount\""));
        assertTrue(body.contains("\"successCount\""));
        assertTrue(body.contains("\"failureCount\""));
        assertTrue(body.contains("\"matchedCount\""));
        assertTrue(body.contains("\"mismatchCount\""));
        assertTrue(body.contains("\"matchRate\""));
        assertTrue(body.contains("\"failureRate\""));
        assertTrue(body.contains("\"lastUpdatedTime\""));
        assertTrue(body.contains("\"providerAggregation\""));
        assertTrue(body.contains("\"decisionAggregation\""));
        assertTrue(body.contains("\"thresholdDecision\"")
                || body.contains("\"promotionCandidate\""));
    }

    @Test
    void shouldNotContainBlacklistedFieldsInResponse() {
        String body = getEndpointBody().toLowerCase(Locale.ROOT);

        assertFalse(body.contains("apikey"));
        assertFalse(body.contains("api_key"));
        assertFalse(body.contains("baseurl"));
        assertFalse(body.contains("base_url"));
        assertFalse(body.contains("authorization"));
        assertFalse(body.contains("prompt"));
        assertFalse(body.contains("task"));
        assertFalse(body.contains("documentcontent"));
        assertFalse(body.contains("document_content"));
        assertFalse(body.contains("modelrawresponse"));
        assertFalse(body.contains("model_raw_response"));
        assertFalse(body.contains("userid"));
        assertFalse(body.contains("user_id"));
        assertFalse(body.contains("documentid"));
        assertFalse(body.contains("document_id"));
        assertFalse(body.contains("sessionid"));
        assertFalse(body.contains("session_id"));
        assertFalse(body.contains("taskinput"));
        assertFalse(body.contains("task_input"));
        assertFalse(body.contains("finalanswer"));
        assertFalse(body.contains("final_answer"));
    }

    @Test
    void shouldReturnZeroMetricsWhenNoneRecorded() {
        String body = getEndpointBody();

        assertTrue(body.contains("\"totalCount\":0"));
        assertTrue(body.contains("\"providerAggregation\":{}"));
        assertTrue(body.contains("\"decisionAggregation\":{}"));
    }

    @Test
    void shouldNotTriggerRealProvider() {
        String body = getEndpointBody().toLowerCase(Locale.ROOT);

        assertFalse(body.contains("apikey"));
        assertFalse(body.contains("authorization"));
        assertFalse(body.contains("baseurl"));
        assertFalse(body.contains("modelrawresponse"));
    }

    @Test
    void shouldNotChangeMetricsCount() {
        SelectorMetricsSnapshot before = metricsCollector.snapshot();

        getEndpointBody();

        SelectorMetricsSnapshot after = metricsCollector.snapshot();
        assertEquals(before.totalCount(), after.totalCount());
        assertEquals(before.successCount(), after.successCount());
        assertEquals(before.failureCount(), after.failureCount());
        assertEquals(before.matchedCount(), after.matchedCount());
        assertEquals(before.mismatchCount(), after.mismatchCount());
    }

    private String getEndpointBody() {
        ResponseEntity<String> response = restTemplate.getForEntity(ENDPOINT_PATH, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            AgentSelectorShadowEndpoint.class,
            SelectorMetricsCollector.class,
            SelectorMetricsDebugReporter.class,
            SelectorShadowThresholdPolicy.class
    })
    static class TestApplication {
    }
}
