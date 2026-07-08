package com.docpilot.backend.ai.agent.tool.openai;

import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagTool;
import com.docpilot.backend.ai.agent.tool.DocumentSearchTool;
import com.docpilot.backend.ai.agent.tool.spec.DefaultToolSpecProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiToolSchemaAdapterTest {

    private final OpenAiToolSchemaAdapter adapter = new OpenAiToolSchemaAdapter();

    @Test
    void shouldConvertVisibleToolSpecsToOpenAiFunctionTools() {
        var tools = adapter.toTools(new DefaultToolSpecProvider().getToolSpecs());

        assertThat(tools).extracting(tool -> tool.function().name())
                .contains("document_status_tool", "document_summary_tool", "document_qa_tool", DocumentSearchTool.TOOL_NAME, DocumentRagQaTool.TOOL_NAME)
                .doesNotContain(DocumentRagTool.TOOL_NAME);
        OpenAiToolDefinition ragTool = tools.stream()
                .filter(tool -> DocumentRagQaTool.TOOL_NAME.equals(tool.function().name()))
                .findFirst()
                .orElseThrow();
        assertThat(ragTool.type()).isEqualTo("function");
        assertThat(ragTool.function().description()).contains("RagScopeGuard");
        assertThat(ragTool.function().parameters().type()).isEqualTo("object");
        assertThat(ragTool.function().parameters().required()).contains("userId", "documentId", "question");
    }

    @Test
    void shouldConvertDocumentSearchToolSchema() {
        var tool = adapter.toTools(new DefaultToolSpecProvider().getToolSpecs()).stream()
                .filter(item -> DocumentSearchTool.TOOL_NAME.equals(item.function().name()))
                .findFirst()
                .orElseThrow();
        var properties = tool.function().parameters().properties();

        assertThat(tool.function().description()).contains("without generating an answer");
        assertThat(tool.function().parameters().required()).contains("userId", "documentId", "query");
        assertThat(properties.get("documentId").type()).isEqualTo("integer");
        assertThat(properties.get("query").type()).isEqualTo("string");
        assertThat(properties.get("topK").type()).isEqualTo("integer");
    }

    @Test
    void shouldMapInternalTypesToJsonSchemaTypes() {
        var tool = adapter.toTools(new DefaultToolSpecProvider().getToolSpecs()).stream()
                .filter(item -> DocumentRagQaTool.TOOL_NAME.equals(item.function().name()))
                .findFirst()
                .orElseThrow();
        var properties = tool.function().parameters().properties();

        assertThat(properties.get("userId").type()).isEqualTo("integer");
        assertThat(properties.get("documentId").type()).isEqualTo("integer");
        assertThat(properties.get("question").type()).isEqualTo("string");
        assertThat(properties.get("topK").type()).isEqualTo("integer");
        assertThat(properties.get("indexVersion").description()).contains("int|null");
    }
}
