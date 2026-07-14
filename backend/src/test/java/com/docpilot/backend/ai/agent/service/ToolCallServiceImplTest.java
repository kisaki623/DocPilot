package com.docpilot.backend.ai.agent.service;

import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.service.impl.ToolCallServiceImpl;
import com.docpilot.backend.ai.agent.tool.AgentTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagTool;
import com.docpilot.backend.ai.agent.tool.DocumentSearchTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.KnowledgeBaseSearchTool;
import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import com.docpilot.backend.ai.agent.tool.spec.DefaultToolSpecProvider;
import com.docpilot.backend.ai.agent.tool.spec.ToolArgumentValidator;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallStatus;
import com.docpilot.backend.ai.agent.tool.spec.ToolInputMapper;
import com.docpilot.backend.ai.agent.tool.spec.ToolSpecRegistry;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolCallServiceImplTest {

    @Test
    void shouldListVisibleToolsAndMarkCallableSubset() {
        ToolCallServiceImpl service = serviceWithTools(List.of(
                stubTool("document_status_tool", input -> input),
                stubTool("document_summary_tool", input -> input),
                stubTool("document_qa_tool", input -> input),
                stubTool(DocumentSearchTool.TOOL_NAME, input -> input),
                stubTool(KnowledgeBaseSearchTool.TOOL_NAME, input -> input),
                stubTool(DocumentRagQaTool.TOOL_NAME, input -> input),
                stubTool(DocumentRagTool.TOOL_NAME, input -> input)
        ));

        var tools = service.listTools();

        assertThat(tools).extracting("name")
                .contains("document_status_tool", "document_summary_tool", "document_qa_tool", DocumentSearchTool.TOOL_NAME, KnowledgeBaseSearchTool.TOOL_NAME, DocumentRagQaTool.TOOL_NAME)
                .doesNotContain(DocumentRagTool.TOOL_NAME);
        assertThat(tools.stream().filter(item -> item.isCallableByToolCallApi()).map(item -> item.getName()))
                .containsExactlyInAnyOrder("document_status_tool", DocumentSearchTool.TOOL_NAME, KnowledgeBaseSearchTool.TOOL_NAME, DocumentRagQaTool.TOOL_NAME);
    }

    @Test
    void shouldCallDocumentStatusTool() {
        AtomicReference<Object> capturedInput = new AtomicReference<>();
        ToolCallServiceImpl service = serviceWithTools(List.of(
                stubTool("document_status_tool", input -> {
                    capturedInput.set(input);
                    return new DocumentStatusTool.StatusResult(101L, "Doc", "SUCCESS", true, "ok", "summary", "content");
                }),
                stubTool("document_summary_tool", input -> input),
                stubTool("document_qa_tool", input -> input),
                stubTool(DocumentSearchTool.TOOL_NAME, input -> input),
                stubTool(KnowledgeBaseSearchTool.TOOL_NAME, input -> input),
                stubTool(DocumentRagQaTool.TOOL_NAME, input -> input)
        ));
        ToolCallRequest request = request("document_status_tool", Map.of("documentId", "101"));

        ToolCallResult result = service.call(7L, request);

        assertEquals(ToolCallStatus.SUCCESS, result.status());
        assertThat(result.outputSummary()).contains("parseStatus=SUCCESS").contains("parseReady=true");
        assertThat(capturedInput.get()).isEqualTo(new DocumentStatusTool.StatusInput(7L, 101L));
    }

    @Test
    void shouldCallRagQaToolWithNormalizedTopKAndExposeEvidence() {
        AtomicReference<DocumentRagQaTool.RagQaInput> capturedInput = new AtomicReference<>();
        var hit = new com.docpilot.backend.ai.rag.RagRetrievalHit(
                1, "vec-1", 0.88d, 7L, 101L, 2, 300L, 0, "snippet", "hash", 0, 7, 2, "mock"
        );
        var citation = hit.toCitation();
        ToolCallServiceImpl service = serviceWithTools(List.of(
                stubTool("document_status_tool", input -> input),
                stubTool("document_summary_tool", input -> input),
                stubTool("document_qa_tool", input -> input),
                stubTool(DocumentSearchTool.TOOL_NAME, input -> input),
                stubTool(KnowledgeBaseSearchTool.TOOL_NAME, input -> input),
                stubTool(DocumentRagQaTool.TOOL_NAME, input -> {
                    capturedInput.set((DocumentRagQaTool.RagQaInput) input);
                    return new DocumentRagQaTool.RagQaResult(
                            7L,
                            101L,
                            "cache?",
                            "answer",
                            "s1",
                            10,
                            2,
                            List.of(hit),
                            List.of(citation),
                            false,
                            false,
                            "",
                            "topK=10, hitCount=1, citationCount=1"
                    );
                })
        ));
        ToolCallRequest request = request(DocumentRagQaTool.TOOL_NAME, Map.of(
                "documentId", 101L,
                "question", " cache? ",
                "topK", 99,
                "indexVersion", 2,
                "sessionId", "s1"
        ));

        ToolCallResult result = service.call(7L, request);

        assertEquals(ToolCallStatus.SUCCESS, result.status());
        assertThat(capturedInput.get().topK()).isEqualTo(10);
        assertThat(capturedInput.get().question()).isEqualTo("cache?");
        assertThat(result.citations()).isEqualTo(List.of(citation));
        assertThat(result.retrievalHits()).isEqualTo(List.of(hit));
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void shouldCallDocumentSearchToolWithSafeEvidence() {
        AtomicReference<DocumentSearchTool.SearchInput> capturedInput = new AtomicReference<>();
        var hit = new DocumentSearchTool.SearchHit(
                1,
                0.88d,
                "Doc",
                300L,
                0,
                2,
                "Ops",
                "page=2#block=1",
                "PARAGRAPH",
                "quote",
                "snippet",
                "hash"
        );
        var citation = new DocumentSearchTool.SearchCitation(
                1,
                101L,
                "Doc",
                2,
                300L,
                0,
                2,
                "Ops",
                "page=2#block=1",
                "PARAGRAPH",
                "quote",
                "snippet",
                "hash",
                0.88d
        );
        ToolCallServiceImpl service = serviceWithTools(List.of(
                stubTool("document_status_tool", input -> input),
                stubTool("document_summary_tool", input -> input),
                stubTool("document_qa_tool", input -> input),
                stubTool(DocumentSearchTool.TOOL_NAME, input -> {
                    capturedInput.set((DocumentSearchTool.SearchInput) input);
                    return new DocumentSearchTool.SearchResult(
                            7L,
                            101L,
                            "cache?",
                            10,
                            2,
                            false,
                            1,
                            1,
                            List.of(hit),
                            List.of(citation),
                            "topK=10, hitCount=1, citationCount=1"
                    );
                }),
                stubTool(KnowledgeBaseSearchTool.TOOL_NAME, input -> input),
                stubTool(DocumentRagQaTool.TOOL_NAME, input -> input)
        ));
        ToolCallRequest request = request(DocumentSearchTool.TOOL_NAME, Map.of(
                "documentId", 101L,
                "query", " cache? ",
                "topK", 99,
                "indexVersion", 2
        ));

        ToolCallResult result = service.call(7L, request);

        assertEquals(ToolCallStatus.SUCCESS, result.status());
        assertThat(capturedInput.get().topK()).isEqualTo(10);
        assertThat(capturedInput.get().query()).isEqualTo("cache?");
        assertThat(result.citations()).isEqualTo(List.of(citation));
        assertThat(result.retrievalHits()).isEqualTo(List.of(hit));
        assertThat(result.outputSummary()).contains("hitCount=1");
    }

    @Test
    void shouldCallKnowledgeBaseSearchToolWithSafeEvidence() {
        AtomicReference<KnowledgeBaseSearchTool.SearchInput> capturedInput = new AtomicReference<>();
        var hit = new KnowledgeBaseSearchTool.SearchHit(
                1,
                0.88d,
                101L,
                "Doc",
                300L,
                0,
                "quote",
                "snippet",
                "hash",
                0.8d,
                0.2d,
                0.9d,
                null
        );
        var citation = new KnowledgeBaseSearchTool.SearchCitation(
                1,
                10L,
                101L,
                "Doc",
                2,
                300L,
                0,
                "quote",
                "snippet",
                "hash",
                0.88d,
                0.8d,
                0.2d,
                0.9d,
                null
        );
        ToolCallServiceImpl service = serviceWithTools(List.of(
                stubTool("document_status_tool", input -> input),
                stubTool("document_summary_tool", input -> input),
                stubTool("document_qa_tool", input -> input),
                stubTool(DocumentSearchTool.TOOL_NAME, input -> input),
                stubTool(KnowledgeBaseSearchTool.TOOL_NAME, input -> {
                    capturedInput.set((KnowledgeBaseSearchTool.SearchInput) input);
                    return new KnowledgeBaseSearchTool.SearchResult(
                            7L,
                            10L,
                            "cache?",
                            10,
                            2,
                            List.of(101L),
                            Map.of(101L, 1),
                            false,
                            1,
                            1,
                            "hybrid",
                            true,
                            "mock-rerank",
                            true,
                            3,
                            2,
                            List.of(hit),
                            List.of(citation),
                            "topK=10, hitCount=1, citationCount=1"
                    );
                }),
                stubTool(DocumentRagQaTool.TOOL_NAME, input -> input)
        ));
        ToolCallRequest request = request(KnowledgeBaseSearchTool.TOOL_NAME, Map.of(
                "knowledgeBaseId", 10L,
                "query", " cache? ",
                "topK", 99,
                "indexVersion", 2,
                "multiQueryEnabled", "true",
                "maxQueryVariants", 3
        ));

        ToolCallResult result = service.call(7L, request);

        assertEquals(ToolCallStatus.SUCCESS, result.status());
        assertThat(capturedInput.get().topK()).isEqualTo(10);
        assertThat(capturedInput.get().query()).isEqualTo("cache?");
        assertThat(capturedInput.get().multiQueryEnabled()).isTrue();
        assertThat(result.citations()).isEqualTo(List.of(citation));
        assertThat(result.retrievalHits()).isEqualTo(List.of(hit));
        assertThat(result.outputSummary()).contains("hitCount=1");
    }

    @Test
    void shouldReturnFailedResultWhenRagToolRejectsScope() {
        ToolCallServiceImpl service = serviceWithTools(List.of(
                stubTool("document_status_tool", input -> input),
                stubTool("document_summary_tool", input -> input),
                stubTool("document_qa_tool", input -> input),
                stubTool(DocumentSearchTool.TOOL_NAME, input -> input),
                stubTool(KnowledgeBaseSearchTool.TOOL_NAME, input -> input),
                stubTool(DocumentRagQaTool.TOOL_NAME, input -> {
                    throw new BusinessException(ErrorCode.DOCUMENT_FORBIDDEN);
                })
        ));

        ToolCallResult result = service.call(7L, request(DocumentRagQaTool.TOOL_NAME, Map.of(
                "documentId", 101L,
                "question", "forbidden"
        )));

        assertEquals(ToolCallStatus.FAILED, result.status());
        assertEquals("DOCUMENT_FORBIDDEN", result.errorType());
        assertThat(result.citations()).isEmpty();
        assertThat(result.retrievalHits()).isEmpty();
    }

    @Test
    void shouldRejectUnknownUnsupportedAndInvalidArguments() {
        ToolCallServiceImpl service = serviceWithTools(List.of(
                stubTool("document_status_tool", input -> input),
                stubTool("document_summary_tool", input -> input),
                stubTool("document_qa_tool", input -> input),
                stubTool(DocumentSearchTool.TOOL_NAME, input -> input),
                stubTool(KnowledgeBaseSearchTool.TOOL_NAME, input -> input),
                stubTool(DocumentRagQaTool.TOOL_NAME, input -> input)
        ));

        assertThrows(BusinessException.class, () -> service.call(7L, request("missing_tool", Map.of())));
        assertThrows(BusinessException.class, () -> service.call(7L, request("document_summary_tool", Map.of("task", "summary"))));
        assertThrows(BusinessException.class, () -> service.call(7L, request(DocumentRagQaTool.TOOL_NAME, Map.of("documentId", 101L))));
        assertThrows(BusinessException.class, () -> service.call(7L, request(DocumentSearchTool.TOOL_NAME, Map.of("documentId", 101L))));
        assertThrows(BusinessException.class, () -> service.call(7L, request(KnowledgeBaseSearchTool.TOOL_NAME, Map.of("knowledgeBaseId", 10L))));
        assertThrows(BusinessException.class, () -> service.call(7L, request(DocumentRagQaTool.TOOL_NAME, Map.of(
                "documentId", 101L,
                "question", "cache?",
                "userId", 8L
        ))));
    }

    private ToolCallServiceImpl serviceWithTools(List<AgentTool<?, ?>> tools) {
        ToolRegistry toolRegistry = new ToolRegistry(tools);
        return new ToolCallServiceImpl(
                new ToolSpecRegistry(new DefaultToolSpecProvider(), toolRegistry),
                toolRegistry,
                new ToolArgumentValidator(),
                new ToolInputMapper()
        );
    }

    private ToolCallRequest request(String toolName, Map<String, Object> arguments) {
        ToolCallRequest request = new ToolCallRequest();
        request.setToolName(toolName);
        request.setArguments(arguments);
        return request;
    }

    private AgentTool<Object, Object> stubTool(String toolName, java.util.function.Function<Object, Object> executor) {
        return new AgentTool<>() {
            @Override
            public String getToolName() {
                return toolName;
            }

            @Override
            public Object execute(Object input) {
                return executor.apply(input);
            }
        };
    }
}
