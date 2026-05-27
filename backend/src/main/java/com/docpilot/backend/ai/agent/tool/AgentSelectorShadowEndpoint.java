package com.docpilot.backend.ai.agent.tool;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "agentSelectorShadow", enableByDefault = false)
public class AgentSelectorShadowEndpoint {

    private final SelectorMetricsDebugReporter debugReporter;

    public AgentSelectorShadowEndpoint(SelectorMetricsDebugReporter debugReporter) {
        this.debugReporter = debugReporter;
    }

    @ReadOperation
    public SelectorMetricsDebugSnapshot shadowMetrics() {
        return debugReporter.dump();
    }
}
