package com.docpilot.backend.ai.agent.tool;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class SelectorMetricsDebugReporter {

    private final SelectorMetricsCollector metricsCollector;
    private final SelectorShadowThresholdPolicy thresholdPolicy;

    @Autowired
    public SelectorMetricsDebugReporter(SelectorMetricsCollector metricsCollector) {
        this(metricsCollector, new SelectorShadowThresholdPolicy());
    }

    SelectorMetricsDebugReporter(SelectorMetricsCollector metricsCollector,
                                 SelectorShadowThresholdPolicy thresholdPolicy) {
        this.metricsCollector = metricsCollector;
        this.thresholdPolicy = thresholdPolicy;
    }

    public SelectorMetricsDebugSnapshot dump() {
        SelectorMetricsSnapshot snapshot = metricsCollector.snapshot();
        SelectorShadowThresholdDecision thresholdDecision = thresholdPolicy.evaluate(snapshot);
        return SelectorMetricsDebugSnapshot.from(snapshot, thresholdDecision);
    }
}
