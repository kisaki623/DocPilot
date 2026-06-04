package com.docpilot.backend.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDefinitionProviderTest {

    private final ToolDefinitionProvider provider = new ToolDefinitionProvider(new ToolRegistry(List.of(
            new StubTool("document_status_tool"),
            new StubTool("document_summary_tool"),
            new StubTool("document_qa_tool"),
            new StubTool(DocumentRagQaTool.TOOL_NAME)
    )));

    @Test
    void shouldReturnDefinitionsForCurrentTools() {
        List<ToolDefinition> definitions = provider.getAllDefinitions();

        assertEquals(4, definitions.size());
        assertTrue(definitions.stream().anyMatch(definition -> "document_status_tool".equals(definition.toolName())));
        assertTrue(definitions.stream().anyMatch(definition -> "document_summary_tool".equals(definition.toolName())));
        assertTrue(definitions.stream().anyMatch(definition -> "document_qa_tool".equals(definition.toolName())));
        assertTrue(definitions.stream().anyMatch(definition -> DocumentRagQaTool.TOOL_NAME.equals(definition.toolName())));
    }

    @Test
    void shouldUseUniqueToolNames() {
        List<ToolDefinition> definitions = provider.getAllDefinitions();
        Set<String> toolNames = new HashSet<>();

        for (ToolDefinition definition : definitions) {
            assertTrue(toolNames.add(definition.toolName()), "Duplicate toolName: " + definition.toolName());
        }
    }

    @Test
    void shouldProvideNonBlankMetadata() {
        for (ToolDefinition definition : provider.getAllDefinitions()) {
            assertFalse(definition.description().isBlank());
            assertFalse(definition.inputSchemaText().isBlank());
            assertFalse(definition.outputSchemaText().isBlank());
            assertTrue(definition.safeForLlmSelection());
        }
    }

    @Test
    void qaDescriptionShouldNotClaimDangerousCapabilities() {
        ToolDefinition qaDefinition = provider.getByToolName("document_qa_tool");

        assertFalse(qaDefinition.description().contains("执行SQL"));
        assertFalse(qaDefinition.description().contains("系统命令"));
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
