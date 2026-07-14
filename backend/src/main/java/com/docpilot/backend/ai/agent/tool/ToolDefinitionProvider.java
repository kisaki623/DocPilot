package com.docpilot.backend.ai.agent.tool;

import com.docpilot.backend.ai.agent.tool.spec.DefaultToolSpecProvider;
import com.docpilot.backend.ai.agent.tool.spec.ToolDefinitionAdapter;
import com.docpilot.backend.ai.agent.tool.spec.ToolSpecRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolDefinitionProvider {

    private final ToolSpecRegistry toolSpecRegistry;
    private final ToolDefinitionAdapter adapter;

    @Autowired
    public ToolDefinitionProvider(ToolSpecRegistry toolSpecRegistry) {
        this.toolSpecRegistry = toolSpecRegistry;
        this.adapter = new ToolDefinitionAdapter();
    }

    public ToolDefinitionProvider(ToolRegistry toolRegistry) {
        this(new ToolSpecRegistry(new DefaultToolSpecProvider(), toolRegistry));
    }

    public List<ToolDefinition> getAllDefinitions() {
        return toolSpecRegistry.listLlmSelectable().stream()
                .map(adapter::toDefinition)
                .toList();
    }

    public ToolDefinition getByToolName(String toolName) {
        return adapter.toDefinition(toolSpecRegistry.getLlmSelectable(toolName));
    }
}
