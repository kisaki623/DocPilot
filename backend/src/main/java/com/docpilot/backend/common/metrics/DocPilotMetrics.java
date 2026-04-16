package com.docpilot.backend.common.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class DocPilotMetrics {

    private static final String PARSE_STAGE_DURATION = "docpilot.parse.stage.duration";
    private static final String MQ_CONSUME_TOTAL = "docpilot.mq.consume.total";
    private static final String MQ_OUTBOX_DISPATCH_TOTAL = "docpilot.mq.outbox.dispatch.total";
    private static final String LOCK_COMPETITION_TOTAL = "docpilot.lock.competition.total";
    private static final String RATE_LIMIT_TRIGGER_TOTAL = "docpilot.ratelimit.trigger.total";
    private static final String AI_CALL_TOTAL = "docpilot.ai.call.total";
    private static final String AI_CALL_DURATION = "docpilot.ai.call.duration";
    private static final String AI_RETRY_TOTAL = "docpilot.ai.retry.total";
    private static final String AI_TOKEN_USAGE = "docpilot.ai.token.usage";
    private static final String AI_COST_ESTIMATE = "docpilot.ai.cost.estimate";
    private static final String CACHE_ACCESS_TOTAL = "docpilot.cache.access.total";

    private DocPilotMetrics() {
    }

    public static void recordParseStageDuration(String stage, long durationNanos) {
        if (durationNanos < 0) {
            return;
        }
        Timer.builder(PARSE_STAGE_DURATION)
                .tag("stage", normalize(stage))
                .register(Metrics.globalRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public static void recordMqConsume(String result) {
        Metrics.counter(MQ_CONSUME_TOTAL, "result", normalize(result)).increment();
    }

    public static void recordOutboxDispatch(String trigger, String result) {
        Metrics.counter(
                MQ_OUTBOX_DISPATCH_TOTAL,
                "trigger", normalize(trigger),
                "result", normalize(result)
        ).increment();
    }

    public static void recordLockCompetition(String operation, String result) {
        Metrics.counter(
                LOCK_COMPETITION_TOTAL,
                "operation", normalize(operation),
                "result", normalize(result)
        ).increment();
    }

    public static void recordRateLimitTrigger(String scene) {
        Metrics.counter(RATE_LIMIT_TRIGGER_TOTAL, "scene", normalize(scene)).increment();
    }

    public static void recordAiCall(String scene, String result, long durationNanos) {
        Metrics.counter(
                AI_CALL_TOTAL,
                "scene", normalize(scene),
                "result", normalize(result)
        ).increment();

        if (durationNanos >= 0) {
            Timer.builder(AI_CALL_DURATION)
                    .tag("scene", normalize(scene))
                    .tag("result", normalize(result))
                    .register(Metrics.globalRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    public static void recordAiRetry(String scene) {
        Metrics.counter(AI_RETRY_TOTAL, "scene", normalize(scene)).increment();
    }

    public static void recordAiTokenUsage(String scene, String tokenType, int tokenCount) {
        if (tokenCount <= 0) {
            return;
        }
        DistributionSummary.builder(AI_TOKEN_USAGE)
                .baseUnit("tokens")
                .tag("scene", normalize(scene))
                .tag("type", normalize(tokenType))
                .register(Metrics.globalRegistry)
                .record(tokenCount);
    }

    public static void recordAiCost(String scene, String currency, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        DistributionSummary.builder(AI_COST_ESTIMATE)
                .baseUnit("currency")
                .tag("scene", normalize(scene))
                .tag("currency", normalize(currency))
                .register(Metrics.globalRegistry)
                .record(amount);
    }

    public static void recordCacheAccess(String scene, String result) {
        Metrics.counter(
                CACHE_ACCESS_TOTAL,
                "scene", normalize(scene),
                "result", normalize(result)
        ).increment();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

