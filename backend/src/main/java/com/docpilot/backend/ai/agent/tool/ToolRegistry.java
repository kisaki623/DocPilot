package com.docpilot.backend.ai.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool<?, ?>> toolMap = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool<?, ?>> tools) {
        for (AgentTool<?, ?> tool : tools) {
            toolMap.put(tool.getToolName(), tool);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends AgentTool<?, ?>> T get(String toolName) {
        AgentTool<?, ?> tool = toolMap.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown agent tool: " + toolName);
        }
        return (T) tool;
    }

    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(toolMap.keySet());
    }

    public int size() {
        return toolMap.size();
    }
}
