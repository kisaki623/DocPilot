package com.docpilot.backend.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocPilotMetricsTest {

    @Test
    void shouldRecordCoreMetricsToGlobalRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);
        try {
            DocPilotMetrics.recordLockCompetition("create", "success");
            DocPilotMetrics.recordRateLimitTrigger("ai_qa");
            DocPilotMetrics.recordOutboxDispatch("scan", "failed");
            DocPilotMetrics.recordMqConsume("success");
            DocPilotMetrics.recordCacheAccess("qa_answer", "hit");
            DocPilotMetrics.recordAiRetry("qa.answer");
            DocPilotMetrics.recordAiCall("qa.answer", "success", 1_000_000L);
            DocPilotMetrics.recordAiTokenUsage("qa.model", "prompt", 120);
            DocPilotMetrics.recordAiCost("qa.model", "usd", 0.0024D);
            DocPilotMetrics.recordParseStageDuration("PARSING", 2_000_000L);

            Counter lockCounter = registry.find("docpilot.lock.competition.total")
                    .tags("operation", "create", "result", "success")
                    .counter();
            Counter rateLimitCounter = registry.find("docpilot.ratelimit.trigger.total")
                    .tags("scene", "ai_qa")
                    .counter();
            Counter outboxCounter = registry.find("docpilot.mq.outbox.dispatch.total")
                    .tags("trigger", "scan", "result", "failed")
                    .counter();
            Counter aiRetryCounter = registry.find("docpilot.ai.retry.total")
                    .tags("scene", "qa.answer")
                    .counter();
            Timer aiDuration = registry.find("docpilot.ai.call.duration")
                    .tags("scene", "qa.answer", "result", "success")
                    .timer();
            DistributionSummary tokenSummary = registry.find("docpilot.ai.token.usage")
                    .tags("scene", "qa.model", "type", "prompt")
                    .summary();
            DistributionSummary costSummary = registry.find("docpilot.ai.cost.estimate")
                    .tags("scene", "qa.model", "currency", "usd")
                    .summary();
            Timer parseStage = registry.find("docpilot.parse.stage.duration")
                    .tags("stage", "parsing")
                    .timer();

            assertEquals(1.0D, lockCounter == null ? 0.0D : lockCounter.count());
            assertEquals(1.0D, rateLimitCounter == null ? 0.0D : rateLimitCounter.count());
            assertEquals(1.0D, outboxCounter == null ? 0.0D : outboxCounter.count());
            assertEquals(1.0D, aiRetryCounter == null ? 0.0D : aiRetryCounter.count());
            assertEquals(1L, aiDuration == null ? 0L : aiDuration.count());
            assertEquals(1L, tokenSummary == null ? 0L : tokenSummary.count());
            assertEquals(1L, costSummary == null ? 0L : costSummary.count());
            assertEquals(1L, parseStage == null ? 0L : parseStage.count());
        } finally {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }
}

