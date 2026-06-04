package com.docpilot.backend.ai.agent.tool.spec;

import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagTool;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultToolSpecProviderTest {

    private final DefaultToolSpecProvider provider = new DefaultToolSpecProvider();

    @Test
    void shouldDescribeCurrentAgentTools() {
        Map<String, ToolSpec> specs = provider.getToolSpecs().stream()
                .collect(Collectors.toMap(ToolSpec::name, Function.identity()));

        assertTrue(specs.containsKey("document_status_tool"));
        assertTrue(specs.containsKey("document_summary_tool"));
        assertTrue(specs.containsKey("document_qa_tool"));
        assertTrue(specs.containsKey(DocumentRagQaTool.TOOL_NAME));
        assertTrue(specs.containsKey(DocumentRagTool.TOOL_NAME));
    }

    @Test
    void ragQaSpecShouldUseNewRagWorkflowBoundary() {
        ToolSpec spec = provider.getToolSpecs().stream()
                .filter(item -> DocumentRagQaTool.TOOL_NAME.equals(item.name()))
                .findFirst()
                .orElseThrow();

        assertTrue(spec.description().contains("EmbeddingProvider"));
        assertTrue(spec.description().contains("VectorStoreClient"));
        assertTrue(spec.description().contains("RagScopeGuard"));
        assertEquals(ToolRiskLevel.MEDIUM, spec.riskLevel());
        assertTrue(spec.requiredFields().contains("userId"));
        assertTrue(spec.requiredFields().contains("documentId"));
        assertTrue(spec.requiredFields().contains("question"));
        assertTrue(spec.safeForLlmSelection());
    }

    @Test
    void legacyRagSpecShouldNotBeLlmSelectable() {
        ToolSpec spec = provider.getToolSpecs().stream()
                .filter(item -> DocumentRagTool.TOOL_NAME.equals(item.name()))
                .findFirst()
                .orElseThrow();

        assertFalse(spec.safeForLlmSelection());
        assertTrue(spec.description().contains("Showcase-only"));
    }
}
