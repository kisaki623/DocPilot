package com.docpilot.backend.ai.agent.service;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.service.impl.DocumentAgentServiceImpl;
import com.docpilot.backend.ai.agent.tool.DocumentQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.DocumentSummaryTool;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionParser;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionPromptBuilder;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionResult;
import com.docpilot.backend.ai.agent.tool.LlmToolSelector;
import com.docpilot.backend.ai.agent.tool.SelectorMetricsCollector;
import com.docpilot.backend.ai.agent.tool.ToolDefinition;
import com.docpilot.backend.ai.agent.tool.ToolDefinitionProvider;
import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import com.docpilot.backend.ai.agent.tool.ToolSelector;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAgentLlmExecuteModeTest {

    @Mock
    private DocumentStatusTool documentStatusTool;

    @Mock
    private DocumentSummaryTool documentSummaryTool;

    @Mock
    private DocumentQaTool documentQaTool;

    @Mock
    private DocumentRagTool documentRagTool;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolSelector toolSelector;

    @Mock
    private AgentTaskPersistenceService persistenceService;

    @Mock
    private LlmToolSelector shadowToolSelector;

    @Mock
    private ToolDefinitionProvider toolDefinitionProvider;

    @Mock
    private LlmToolSelectionPromptBuilder realShadowPromptBuilder;

    @Mock
    private LlmToolSelectionParser realShadowParser;

    private final AgentSelectorProperties selectorProperties = new AgentSelectorProperties();

    private DocumentAgentServiceImpl buildService() {
        return new DocumentAgentServiceImpl(
                toolRegistry,
                toolSelector,
                persistenceService,
                selectorProperties,
                shadowToolSelector,
                toolDefinitionProvider,
                new SelectorMetricsCollector(),
                realShadowPromptBuilder,
                realShadowParser
        );
    }

    @Test
    void shouldExecuteSummaryToolFromLlmExecuteMode() {
        enableFakeLlmExecuteMode();
        DocumentAgentServiceImpl service = buildService();

        stubReadyStatus(110L, "summary", "content");
        stubKeywordSelection("qa_tool", "keyword qa reason", "question");
        stubLlmSelection(new LlmToolSelectionResult(
                "summary_tool",
                List.of("document_status_tool", "document_summary_tool"),
                "llm summary reason",
                List.of("summary"),
                0.8d
        ));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubToolNames();
        stubRegistryForSummary();
        stubPersistenceTask();

        var response = service.run(100L, request(110L, "Please summarize this document"));

        assertEquals("summary_tool", response.getDecision());
        assertEquals("qa_tool", response.getPrimaryDecision());
        assertEquals("summary_tool", response.getLlmDecision());
        assertEquals("summary_tool", response.getFinalDecision());
        assertFalse(response.isFallbackUsed());
        assertEquals("llm_execute", response.getExecutionMode());
        assertEquals("llm_execute", response.getToolSelectionSource());
        assertEquals("llm summary reason", response.getRoutingReason());
        verify(documentSummaryTool).execute(any());
        verify(documentQaTool, never()).execute(any());
        verify(documentRagTool, never()).execute(any());
    }

    @Test
    void shouldExecuteQaToolFromLlmExecuteMode() {
        enableFakeLlmExecuteMode();
        DocumentAgentServiceImpl service = buildService();

        stubReadyStatus(111L, "summary", "content");
        stubKeywordSelection("summary_tool", "keyword summary reason", "summary");
        stubLlmSelection(new LlmToolSelectionResult(
                "qa_tool",
                List.of("document_status_tool", "document_qa_tool"),
                "llm qa reason",
                List.of("evidence"),
                0.8d
        ));
        DocumentQaResponse qaResponse = new DocumentQaResponse();
        qaResponse.setDocumentId(111L);
        qaResponse.setSessionId("sess-llm-qa");
        qaResponse.setAnswer("answer");
        qaResponse.setCitations(List.of(new DocumentQaResponse.CitationItem()));
        when(documentQaTool.execute(new DocumentQaTool.QaInput(100L, 111L, "Please answer with evidence", "sess-llm-qa")))
                .thenReturn(qaResponse);
        stubRegistryForQa();
        stubPersistenceTask();

        DocumentAgentRequest request = request(111L, "Please answer with evidence");
        request.setSessionId("sess-llm-qa");
        var response = service.run(100L, request);

        assertEquals("qa_tool", response.getDecision());
        assertEquals("summary_tool", response.getPrimaryDecision());
        assertEquals("qa_tool", response.getLlmDecision());
        assertEquals("qa_tool", response.getFinalDecision());
        assertFalse(response.isFallbackUsed());
        assertEquals("llm_execute", response.getToolSelectionSource());
        assertEquals("answer", response.getFinalAnswer());
        verify(documentQaTool).execute(any());
        verify(documentSummaryTool, never()).execute(any());
        verify(documentRagTool, never()).execute(any());
    }

    @Test
    void shouldExecuteRagToolFromLlmExecuteMode() {
        enableFakeLlmExecuteMode();
        DocumentAgentServiceImpl service = buildService();

        stubReadyStatus(112L, "summary", "Payment content");
        stubKeywordSelection("qa_tool", "keyword qa reason", "question");
        stubLlmSelection(new LlmToolSelectionResult(
                "rag_tool",
                List.of("document_status_tool", DocumentRagTool.TOOL_NAME),
                "llm rag reason",
                List.of("rag"),
                0.8d
        ));
        DocumentRagTool.RetrievedChunk chunk = new DocumentRagTool.RetrievedChunk(
                1,
                0,
                0.9d,
                "Payment content",
                Map.of("contentHash", "hash")
        );
        when(documentRagTool.execute(any())).thenReturn(new DocumentRagTool.RagResult(
                112L,
                1,
                3,
                List.of(chunk),
                List.of(),
                "[1] Payment content",
                "Retrieved 1 chunk(s) from 1 indexed chunk(s)."
        ));
        stubRegistryForRag();
        stubPersistenceTask();

        var response = service.run(100L, request(112L, "RAG retrieve similar chunks"));

        assertEquals("rag_tool", response.getDecision());
        assertEquals("qa_tool", response.getPrimaryDecision());
        assertEquals("rag_tool", response.getLlmDecision());
        assertEquals("rag_tool", response.getFinalDecision());
        assertFalse(response.isFallbackUsed());
        assertEquals("llm_execute", response.getToolSelectionSource());
        assertEquals(1, response.getRagResults().size());
        verify(documentRagTool).execute(any());
        verify(documentQaTool, never()).execute(any());
    }

    @Test
    void shouldFallbackToKeywordWhenProviderThrows() {
        enableFakeLlmExecuteMode();
        DocumentAgentServiceImpl service = buildService();

        stubReadyStatus(113L, "summary", "content");
        stubKeywordSelection("summary_tool", "keyword summary reason", "summary");
        stubToolDefinitions();
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenThrow(new IllegalStateException("provider unavailable"));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubRegistryForSummary();
        stubPersistenceTask();

        var response = service.run(100L, request(113L, "Please summarize this document"));

        assertEquals("summary_tool", response.getDecision());
        assertEquals("summary_tool", response.getPrimaryDecision());
        assertEquals("", response.getLlmDecision());
        assertEquals("summary_tool", response.getFinalDecision());
        assertTrue(response.isFallbackUsed());
        assertEquals("llm_execute_fallback", response.getToolSelectionSource());
        assertTrue(response.getFallbackReason().contains("IllegalStateException"));
        assertTrue(response.getRoutingReason().contains("LLM execute fallback"));
        verify(documentSummaryTool).execute(any());
        verify(documentQaTool, never()).execute(any());
    }

    @Test
    void shouldFallbackToKeywordWhenLlmReturnsUnregisteredToolName() {
        enableFakeLlmExecuteMode();
        DocumentAgentServiceImpl service = buildService();

        stubReadyStatus(114L, "summary", "content");
        stubKeywordSelection("summary_tool", "keyword summary reason", "summary");
        stubLlmSelection(new LlmToolSelectionResult(
                "summary_tool",
                List.of("document_status_tool", "unknown_generated_tool"),
                "llm attempted an unknown tool",
                List.of("summary"),
                0.8d
        ));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubToolNames();
        stubRegistryForSummary();
        stubPersistenceTask();

        var response = service.run(100L, request(114L, "Please summarize this document"));

        assertEquals("summary_tool", response.getDecision());
        assertEquals("summary_tool", response.getPrimaryDecision());
        assertEquals("summary_tool", response.getLlmDecision());
        assertTrue(response.isFallbackUsed());
        assertEquals("llm_execute_fallback", response.getToolSelectionSource());
        assertTrue(response.getFallbackReason().contains("IllegalArgumentException"));
        verify(documentSummaryTool).execute(any());
        verify(documentQaTool, never()).execute(any());
        verify(documentRagTool, never()).execute(any());
    }

    @Test
    void shouldFallbackToKeywordWhenLlmResponseCannotBeParsed() {
        enableFakeLlmExecuteMode();
        DocumentAgentServiceImpl service = buildService();

        stubReadyStatus(115L, "summary", "content");
        stubKeywordSelection("summary_tool", "keyword summary reason", "summary");
        stubToolDefinitions();
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn("Current task: safe test task");
        when(realShadowParser.parse(anyString()))
                .thenThrow(new IllegalArgumentException("not valid JSON"));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubRegistryForSummary();
        stubPersistenceTask();

        var response = service.run(100L, request(115L, "Please summarize this document"));

        assertEquals("summary_tool", response.getDecision());
        assertEquals("", response.getLlmDecision());
        assertTrue(response.isFallbackUsed());
        assertEquals("llm_execute_fallback", response.getToolSelectionSource());
        assertTrue(response.getFallbackReason().contains("IllegalArgumentException"));
        verify(documentSummaryTool).execute(any());
    }

    @Test
    void shouldNotEnterSuccessfulLlmExecutePathWhenProviderDisabled() {
        selectorProperties.setMode(AgentSelectorProperties.MODE_LLM_EXECUTE);
        selectorProperties.setLlmProvider(AgentSelectorProperties.PROVIDER_DISABLED);
        DocumentAgentServiceImpl service = buildService();

        stubReadyStatus(116L, "summary", "content");
        stubKeywordSelection("summary_tool", "keyword summary reason", "summary");
        stubToolDefinitions();
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubRegistryForSummary();
        stubPersistenceTask();

        var response = service.run(100L, request(116L, "Please summarize this document"));

        assertEquals("summary_tool", response.getDecision());
        assertEquals("", response.getLlmDecision());
        assertTrue(response.isFallbackUsed());
        assertEquals("llm_execute_fallback", response.getToolSelectionSource());
        assertTrue(response.getFallbackReason().contains("IllegalStateException"));
        verify(realShadowParser, never()).parse(anyString());
        verify(documentSummaryTool).execute(any());
    }

    @Test
    void shouldFallbackToKeywordWhenOpenAiCompatibleProviderTimesOut() throws Exception {
        try (SlowProvider provider = SlowProvider.start()) {
            selectorProperties.setMode(AgentSelectorProperties.MODE_LLM_EXECUTE);
            selectorProperties.setLlmProvider(AgentSelectorProperties.PROVIDER_OPENAI_COMPATIBLE);
            selectorProperties.setLlmModel("selector-model");
            selectorProperties.setLlmBaseUrl(provider.baseUrl());
            selectorProperties.setLlmApiKey("test-key-not-used");
            selectorProperties.setLlmConnectTimeoutMs(1000);
            selectorProperties.setLlmRequestTimeoutMs(50);
            DocumentAgentServiceImpl service = buildService();

            stubReadyStatus(120L, "summary", "content");
            stubKeywordSelection("summary_tool", "keyword summary reason", "summary");
            stubToolDefinitions();
            when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                    .thenReturn("Current task: safe timeout test task");
            when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                    "Please summarize this document",
                    "summary",
                    "content"
            ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
            stubRegistryForSummary();
            stubPersistenceTask();

            var response = service.run(100L, request(120L, "Please summarize this document"));

            assertEquals("summary_tool", response.getDecision());
            assertEquals("summary_tool", response.getPrimaryDecision());
            assertEquals("", response.getLlmDecision());
            assertEquals("summary_tool", response.getFinalDecision());
            assertTrue(response.isFallbackUsed());
            assertEquals("llm_execute_fallback", response.getToolSelectionSource());
            assertTrue(response.getFallbackReason().contains("IllegalStateException"));
            verify(realShadowParser, never()).parse(anyString());
            verify(documentSummaryTool).execute(any());
            verify(documentQaTool, never()).execute(any());
        }
    }

    @Test
    void shouldKeepKeywordModeBehaviorUnchanged() {
        DocumentAgentServiceImpl service = buildService();

        stubReadyStatus(118L, "summary", "content");
        stubKeywordSelection("summary_tool", "keyword summary reason", "summary");
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubRegistryForSummary();
        stubPersistenceTask();

        var response = service.run(100L, request(118L, "Please summarize this document"));

        assertEquals("summary_tool", response.getDecision());
        assertEquals("summary_tool", response.getPrimaryDecision());
        assertEquals("", response.getLlmDecision());
        assertEquals("summary_tool", response.getFinalDecision());
        assertFalse(response.isFallbackUsed());
        assertEquals("keyword", response.getExecutionMode());
        assertEquals("keyword", response.getToolSelectionSource());
        assertEquals("keyword summary reason", response.getRoutingReason());
        verify(realShadowPromptBuilder, never()).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(realShadowParser, never()).parse(anyString());
        verify(documentSummaryTool).execute(any());
        verify(documentQaTool, never()).execute(any());
        verify(documentRagTool, never()).execute(any());
    }

    @Test
    void shouldFallbackToKeywordWhenDecisionAndToolNamesMismatch() {
        enableFakeLlmExecuteMode();
        DocumentAgentServiceImpl service = buildService();

        stubReadyStatus(119L, "summary", "content");
        stubKeywordSelection("summary_tool", "keyword summary reason", "summary");
        stubLlmSelection(new LlmToolSelectionResult(
                "qa_tool",
                List.of("document_status_tool", DocumentRagTool.TOOL_NAME),
                "llm mismatched tool names",
                List.of("question"),
                0.8d
        ));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubToolNames();
        stubRegistryForSummary();
        stubPersistenceTask();

        var response = service.run(100L, request(119L, "Please summarize this document"));

        assertEquals("summary_tool", response.getDecision());
        assertEquals("summary_tool", response.getPrimaryDecision());
        assertEquals("qa_tool", response.getLlmDecision());
        assertEquals("summary_tool", response.getFinalDecision());
        assertTrue(response.isFallbackUsed());
        assertEquals("llm_execute_fallback", response.getToolSelectionSource());
        assertTrue(response.getFallbackReason().contains("IllegalArgumentException"));
        verify(documentSummaryTool).execute(any());
        verify(documentQaTool, never()).execute(any());
        verify(documentRagTool, never()).execute(any());
    }

    @Test
    void responseShouldNotExposePromptDocumentContentOrSecretsOnFallback() {
        enableFakeLlmExecuteMode();
        DocumentAgentServiceImpl service = buildService();

        String privateContent = "PRIVATE_DOC_CONTENT_SHOULD_NOT_LEAK";
        String privatePrompt = "Current task: prompt text SHOULD_NOT_LEAK";
        String secretMarker = "sk-test-secret-should-not-leak";
        stubReadyStatus(117L, "safe summary", privateContent);
        stubKeywordSelection("summary_tool", "keyword summary reason", "summary");
        stubToolDefinitions();
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(privatePrompt + " " + secretMarker);
        when(realShadowParser.parse(anyString()))
                .thenThrow(new IllegalArgumentException("parser failed without sensitive text"));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "safe summary",
                privateContent
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("safe summary", "summary_field"));
        stubRegistryForSummary();
        stubPersistenceTask();

        var response = service.run(100L, request(117L, "Please summarize this document"));

        assertTrue(response.isFallbackUsed());
        assertEquals("llm_execute_fallback", response.getToolSelectionSource());
        assertNotContainsSensitiveText(response.getRoutingReason(), privateContent, privatePrompt, secretMarker);
        assertNotContainsSensitiveText(response.getFallbackReason(), privateContent, privatePrompt, secretMarker);
        assertNotContainsSensitiveText(response.getFinalAnswer(), privateContent, privatePrompt, secretMarker);
        for (var step : response.getSteps()) {
            assertNotContainsSensitiveText(step.getInputSummary(), privateContent, privatePrompt, secretMarker);
            assertNotContainsSensitiveText(step.getOutputSummary(), privateContent, privatePrompt, secretMarker);
        }
    }

    private void enableFakeLlmExecuteMode() {
        selectorProperties.setMode(AgentSelectorProperties.MODE_LLM_EXECUTE);
        selectorProperties.setLlmProvider(AgentSelectorProperties.PROVIDER_FAKE);
    }

    private DocumentAgentRequest request(Long documentId, String task) {
        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(documentId);
        request.setTask(task);
        return request;
    }

    private void stubReadyStatus(Long documentId, String summary, String content) {
        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, documentId)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        documentId,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        summary,
                        content
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
    }

    private void stubKeywordSelection(String decision, String reason, String keyword) {
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                decision,
                toolNamesForDecision(decision),
                reason,
                List.of(keyword)
        ));
    }

    private void stubLlmSelection(LlmToolSelectionResult result) {
        stubToolDefinitions();
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn("Current task: safe test task");
        when(realShadowParser.parse(anyString())).thenReturn(result);
    }

    private void stubRegistryForSummary() {
        when(toolRegistry.<DocumentStatusTool>get("document_status_tool")).thenReturn(documentStatusTool);
        when(toolRegistry.<DocumentSummaryTool>get("document_summary_tool")).thenReturn(documentSummaryTool);
        when(documentSummaryTool.getToolName()).thenReturn("document_summary_tool");
    }

    private void stubRegistryForQa() {
        stubToolNames();
        when(toolRegistry.<DocumentStatusTool>get("document_status_tool")).thenReturn(documentStatusTool);
        when(toolRegistry.<DocumentQaTool>get("document_qa_tool")).thenReturn(documentQaTool);
        when(documentQaTool.getToolName()).thenReturn("document_qa_tool");
    }

    private void stubRegistryForRag() {
        stubToolNames();
        when(toolRegistry.<DocumentStatusTool>get("document_status_tool")).thenReturn(documentStatusTool);
        when(toolRegistry.<DocumentRagTool>get(DocumentRagTool.TOOL_NAME)).thenReturn(documentRagTool);
        when(documentRagTool.getToolName()).thenReturn(DocumentRagTool.TOOL_NAME);
    }

    private void stubToolNames() {
        when(toolRegistry.getToolNames()).thenReturn(Set.of(
                "document_status_tool",
                "document_summary_tool",
                "document_qa_tool",
                DocumentRagTool.TOOL_NAME
        ));
    }

    private void stubToolDefinitions() {
        when(toolDefinitionProvider.getAllDefinitions()).thenReturn(List.of(
                new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
                new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
                new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true),
                new ToolDefinition(DocumentRagTool.TOOL_NAME, "Document RAG", "Retrieves chunks.", "{}", "{}", true)
        ));
    }

    private List<String> toolNamesForDecision(String decision) {
        return switch (decision) {
            case "summary_tool" -> List.of("document_status_tool", "document_summary_tool");
            case "rag_tool" -> List.of("document_status_tool", DocumentRagTool.TOOL_NAME);
            case "status_only" -> List.of("document_status_tool");
            default -> List.of("document_status_tool", "document_qa_tool");
        };
    }

    private void stubPersistenceTask() {
        AgentTask mockTask = new AgentTask();
        mockTask.setId(1001L);
        when(persistenceService.createTask(anyLong(), anyLong(), anyString(), any())).thenReturn(mockTask);
    }

    private void assertNotContainsSensitiveText(String value, String... sensitiveValues) {
        assertNotNull(value);
        for (String sensitiveValue : sensitiveValues) {
            assertFalse(value.contains(sensitiveValue), "Unexpected sensitive text in response field");
        }
    }

    private record SlowProvider(HttpServer server) implements AutoCloseable {

        static SlowProvider start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                try {
                    Thread.sleep(500L);
                    byte[] bytes = """
                            {"choices":[{"message":{"content":"{\\"decision\\":\\"summary_tool\\",\\"toolNames\\":[\\"document_summary_tool\\"],\\"confidence\\":0.8}"}}]}
                            """.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return new SlowProvider(server);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
