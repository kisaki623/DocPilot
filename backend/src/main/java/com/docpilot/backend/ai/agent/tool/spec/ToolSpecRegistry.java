package com.docpilot.backend.ai.agent.tool.spec;

import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolSpecRegistry {

    private final ToolRegistry toolRegistry;
    private final Map<String, ToolSpec> specs;

    public ToolSpecRegistry(ToolSpecProvider specProvider, ToolRegistry toolRegistry) {
        if (specProvider == null) {
            throw new IllegalArgumentException("specProvider must not be null");
        }
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry must not be null");
        }
        this.toolRegistry = toolRegistry;
        this.specs = buildSpecs(specProvider.getToolSpecs());
        validateLlmSelectableTools();
    }

    public List<ToolSpec> listAll() {
        return List.copyOf(specs.values());
    }

    public List<ToolSpec> listLlmSelectable() {
        return specs.values().stream()
                .filter(this::isLlmSelectable)
                .toList();
    }

    public ToolSpec get(String toolName) {
        ToolSpec spec = specs.get(toolName);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown tool spec: " + toolName);
        }
        return spec;
    }

    public ToolSpec getLlmSelectable(String toolName) {
        ToolSpec spec = get(toolName);
        if (!isLlmSelectable(spec)) {
            throw new IllegalArgumentException("Tool spec is not LLM selectable: " + toolName);
        }
        return spec;
    }

    public boolean isLlmSelectable(String toolName) {
        ToolSpec spec = specs.get(toolName);
        return spec != null && isLlmSelectable(spec);
    }

    private boolean isLlmSelectable(ToolSpec spec) {
        return spec.safeForLlmSelection() && toolRegistry.contains(spec.name());
    }

    private Map<String, ToolSpec> buildSpecs(List<ToolSpec> toolSpecs) {
        Map<String, ToolSpec> result = new LinkedHashMap<>();
        for (ToolSpec spec : toolSpecs == null ? List.<ToolSpec>of() : toolSpecs) {
            ToolSpec previous = result.putIfAbsent(spec.name(), spec);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate tool spec: " + spec.name());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private void validateLlmSelectableTools() {
        for (ToolSpec spec : specs.values()) {
            if (spec.safeForLlmSelection() && !toolRegistry.contains(spec.name())) {
                throw new IllegalArgumentException("LLM selectable tool is not registered: " + spec.name());
            }
        }
    }
}
