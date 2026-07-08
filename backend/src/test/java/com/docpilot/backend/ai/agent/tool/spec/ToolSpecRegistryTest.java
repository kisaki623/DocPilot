package com.docpilot.backend.ai.agent.tool.spec;

import com.docpilot.backend.ai.agent.tool.AgentTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagTool;
import com.docpilot.backend.ai.agent.tool.DocumentSearchTool;
import com.docpilot.backend.ai.agent.tool.KnowledgeBaseSearchTool;
import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSpecRegistryTest {

    @Test
    void shouldListOnlyLlmSelectableRegisteredSpecs() {
        ToolSpecRegistry registry = new ToolSpecRegistry(new DefaultToolSpecProvider(), toolRegistryWithCurrentTools());

        List<ToolSpec> selectable = registry.listLlmSelectable();

        assertEquals(6, selectable.size());
        assertTrue(selectable.stream().anyMatch(spec -> DocumentSearchTool.TOOL_NAME.equals(spec.name())));
        assertTrue(selectable.stream().anyMatch(spec -> KnowledgeBaseSearchTool.TOOL_NAME.equals(spec.name())));
        assertTrue(selectable.stream().anyMatch(spec -> DocumentRagQaTool.TOOL_NAME.equals(spec.name())));
        assertFalse(selectable.stream().anyMatch(spec -> DocumentRagTool.TOOL_NAME.equals(spec.name())));
        assertFalse(registry.isLlmSelectable(DocumentRagTool.TOOL_NAME));
    }

    @Test
    void shouldRejectDuplicateSpecNames() {
        ToolSpec duplicate = simpleSpec("duplicate_tool", true);
        ToolSpecProvider provider = () -> List.of(duplicate, duplicate);
        ToolRegistry toolRegistry = new ToolRegistry(List.of(new StubTool("duplicate_tool")));

        assertThrows(IllegalArgumentException.class, () -> new ToolSpecRegistry(provider, toolRegistry));
    }

    @Test
    void shouldRejectLlmSelectableSpecWithoutExecutableTool() {
        ToolSpecProvider provider = () -> List.of(simpleSpec("missing_tool", true));
        ToolRegistry toolRegistry = new ToolRegistry(List.of());

        assertThrows(IllegalArgumentException.class, () -> new ToolSpecRegistry(provider, toolRegistry));
    }

    @Test
    void shouldAllowNonSelectableLegacySpecWithoutExecutableTool() {
        ToolSpecProvider provider = () -> List.of(simpleSpec("legacy_tool", false));
        ToolSpecRegistry registry = new ToolSpecRegistry(provider, new ToolRegistry(List.of()));

        assertEquals("legacy_tool", registry.get("legacy_tool").name());
        assertFalse(registry.isLlmSelectable("legacy_tool"));
    }

    private ToolRegistry toolRegistryWithCurrentTools() {
        return new ToolRegistry(List.of(
                new StubTool("document_status_tool"),
                new StubTool("document_summary_tool"),
                new StubTool("document_qa_tool"),
                new StubTool(DocumentSearchTool.TOOL_NAME),
                new StubTool(KnowledgeBaseSearchTool.TOOL_NAME),
                new StubTool(DocumentRagQaTool.TOOL_NAME),
                new StubTool(DocumentRagTool.TOOL_NAME)
        ));
    }

    private ToolSpec simpleSpec(String name, boolean safeForLlmSelection) {
        return new ToolSpec(
                name,
                "Test tool",
                "Test tool description.",
                ToolParameterSchema.object(Map.of("input", "String")),
                Set.of("input"),
                ToolResultSchema.object(Map.of("output", "String")),
                ToolRiskLevel.LOW,
                name,
                safeForLlmSelection
        );
    }

    private record StubTool(String toolName) implements AgentTool<Object, Object> {
        @Override
        public String getToolName() {
            return toolName;
        }

        @Override
        public Object execute(Object input) {
            return input;
        }
    }
}
