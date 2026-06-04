package com.docpilot.backend.ai.service;

import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import com.docpilot.backend.ai.agent.service.AgentTaskPersistenceService;
import com.docpilot.backend.ai.agent.service.impl.DocumentAgentServiceImpl;
import com.docpilot.backend.ai.agent.tool.DocumentQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.DocumentSummaryTool;
import com.docpilot.backend.ai.agent.tool.LlmSelectorShadowResult;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionParser;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionPromptBuilder;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionResult;
import com.docpilot.backend.ai.agent.tool.LlmToolSelector;
import com.docpilot.backend.ai.agent.tool.SelectorMetricsCollector;
import com.docpilot.backend.ai.agent.tool.SelectorMetricsSnapshot;
import com.docpilot.backend.ai.agent.tool.SelectorShadowThresholdDecision;
import com.docpilot.backend.ai.agent.tool.SelectorShadowThresholdPolicy;
import com.docpilot.backend.ai.agent.tool.ToolDefinition;
import com.docpilot.backend.ai.agent.tool.ToolDefinitionProvider;
import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import com.docpilot.backend.ai.agent.tool.ToolSelector;
import com.docpilot.backend.ai.rag.RagEvidenceCitation;
import com.docpilot.backend.ai.rag.RagQaAnswer;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.vo.DocumentQaResponse;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAgentServiceImplTest {

    private static final String PRIVATE_RAG_DOC_MARKER = "PRIVATE_AGENT_RAG_DOC_BODY_MARKER";

    @Mock
    private DocumentStatusTool documentStatusTool;

    @Mock
    private DocumentSummaryTool documentSummaryTool;

    @Mock
    private DocumentQaTool documentQaTool;

    @Mock
    private DocumentRagTool documentRagTool;

    @Mock
    private DocumentRagQaTool documentRagQaTool;

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
    private SelectorMetricsCollector selectorMetricsCollector;

    private DocumentAgentServiceImpl buildService() {
        selectorMetricsCollector = new SelectorMetricsCollector();
        return new DocumentAgentServiceImpl(
                toolRegistry,
                toolSelector,
                persistenceService,
                selectorProperties,
                shadowToolSelector,
                toolDefinitionProvider,
                selectorMetricsCollector,
                realShadowPromptBuilder,
                realShadowParser
        );
    }

    private void stubStatusTool() {
        when(toolRegistry.<DocumentStatusTool>get("document_status_tool")).thenReturn(documentStatusTool);
    }

    private void stubSummaryTool() {
        when(toolRegistry.<DocumentSummaryTool>get("document_summary_tool")).thenReturn(documentSummaryTool);
    }

    private void stubQaTool() {
        when(toolRegistry.<DocumentQaTool>get("document_qa_tool")).thenReturn(documentQaTool);
    }

    private void stubRagTool() {
        when(toolRegistry.<DocumentRagTool>get(DocumentRagTool.TOOL_NAME)).thenReturn(documentRagTool);
    }

    private void stubRagQaTool() {
        when(toolRegistry.<DocumentRagQaTool>get(DocumentRagQaTool.TOOL_NAME)).thenReturn(documentRagQaTool);
    }

    private void stubPersistenceTask() {
        AgentTask mockTask = new AgentTask();
        mockTask.setId(1001L);
        when(persistenceService.createTask(anyLong(), anyLong(), anyString(), any())).thenReturn(mockTask);
    }

    private void verifyPersistenceSuccess() {
        verify(persistenceService).createTask(anyLong(), anyLong(), anyString(), any());
        verify(persistenceService, atLeastOnce()).createStep(anyLong(), anyInt(), anyString(), anyString(), anyString(), anyLong(), anyString(), any());
        verify(persistenceService).updateTaskSuccess(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldUseSummaryToolWhenSummaryIntentAndParseSuccess() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(101L);
        request.setTask("Please summarize this document for interview showcase");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 101L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        101L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "This is the summary field.",
                        "This is the full content."
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentSummaryTool.getToolName()).thenReturn("document_summary_tool");
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "summary_tool",
                List.of("document_status_tool", "document_summary_tool"),
                "summary reason",
                List.of("summary")
        ));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document for interview showcase",
                "This is the summary field.",
                "This is the full content."
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("This is the summary field.", "summary_field"));
        stubStatusTool();
        stubSummaryTool();
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals(1001L, response.getTaskId());
        assertEquals("summary_tool", response.getDecision());
        assertNotNull(response.getRoutingReason());
        assertFalse(response.getRoutingReason().isBlank());
        assertEquals("This is the summary field.", response.getFinalAnswer());
        assertEquals(2, response.getSteps().size());
        assertTrue(response.isSuccess());
        assertNotNull(response.getTraceId());
        assertNotNull(response.getStartedAt());
        assertNotNull(response.getFinishedAt());
        assertTrue(response.getTotalDurationMs() >= 0);
        verifyPersistenceSuccess();
        verify(documentQaTool, never()).execute(any());
        verify(documentRagTool, never()).execute(any());
        verify(shadowToolSelector, never()).selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(realShadowPromptBuilder, never()).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        assertEmptySelectorMetrics();
    }

    @Test
    void shouldUseQaToolWhenTaskNeedsEvidence() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(102L);
        request.setTask("Please answer with evidence and cite the key points.");
        request.setSessionId("sess-qa");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 102L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        102L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "content"
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentQaTool.getToolName()).thenReturn("document_qa_tool");
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "qa_tool",
                List.of("document_status_tool", "document_qa_tool"),
                "qa reason",
                List.of("evidence", "cite")
        ));

        DocumentQaResponse qaResponse = new DocumentQaResponse();
        qaResponse.setDocumentId(102L);
        qaResponse.setSessionId("sess-qa");
        qaResponse.setAnswer("This is the answer backed by document evidence.");
        qaResponse.setCitations(List.of(new DocumentQaResponse.CitationItem()));
        when(documentQaTool.execute(new DocumentQaTool.QaInput(100L, 102L, "Please answer with evidence and cite the key points.", "sess-qa")))
                .thenReturn(qaResponse);
        stubStatusTool();
        stubQaTool();
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals(1001L, response.getTaskId());
        assertEquals("qa_tool", response.getDecision());
        assertNotNull(response.getRoutingReason());
        assertFalse(response.getRoutingReason().isBlank());
        assertEquals("This is the answer backed by document evidence.", response.getFinalAnswer());
        assertEquals("sess-qa", response.getSessionId());
        assertNotNull(response.getCitations());
        assertFalse(response.getCitations().isEmpty());
        assertEquals(2, response.getSteps().size());
        assertTrue(response.isSuccess());
        verifyPersistenceSuccess();
        verify(documentRagTool, never()).execute(any());
        verify(realShadowPromptBuilder, never()).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        assertEmptySelectorMetrics();
    }

    @Test
    void shouldUseRagToolForExplicitRetrievalTask() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(109L);
        request.setTask("RAG retrieve topK chunks and show similarity score");
        String documentContent = "Payment clause content. Delivery clause content. " + PRIVATE_RAG_DOC_MARKER;

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 109L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        109L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        documentContent
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentRagQaTool.getToolName()).thenReturn(DocumentRagQaTool.TOOL_NAME);
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "rag_tool",
                List.of("document_status_tool", DocumentRagQaTool.TOOL_NAME),
                "rag reason",
                List.of("RAG", "retrieve")
        ));
        RagRetrievalHit hit = new RagRetrievalHit(
                1,
                "vec-109-1",
                0.91d,
                100L,
                109L,
                2,
                901L,
                0,
                "Payment clause content.",
                "hash",
                0,
                23,
                4,
                "mock-embedding"
        );
        RagEvidenceCitation citation = hit.toCitation();
        when(documentRagQaTool.execute(new DocumentRagQaTool.RagQaInput(
                100L,
                109L,
                "RAG retrieve topK chunks and show similarity score",
                null,
                4,
                2
        ))).thenReturn(new DocumentRagQaTool.RagQaResult(
                100L,
                109L,
                "RAG retrieve topK chunks and show similarity score",
                "Payment clause answer [1]",
                "",
                4,
                2,
                List.of(hit),
                List.of(citation),
                false,
                false,
                "",
                "topK=4, indexVersion=2, hitCount=1, citationCount=1, noEvidence=false, fallbackUsed=false"
        ));
        request.setTopK(4);
        request.setIndexVersion(2);
        stubStatusTool();
        stubRagQaTool();
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals("rag_tool", response.getDecision());
        assertEquals("Payment clause answer [1]", response.getFinalAnswer());
        assertEquals(1, response.getRagResults().size());
        assertEquals(0.91d, response.getRagResults().get(0).getScore());
        assertEquals("Payment clause content.", response.getRagResults().get(0).getSnippet());
        assertEquals("hash", response.getRagResults().get(0).getMetadata().get("contentHash"));
        assertEquals(2, response.getRagResults().get(0).getIndexVersion());
        assertEquals(901L, response.getRagResults().get(0).getChunkId());
        assertEquals(1, response.getRagCitations().size());
        assertEquals("[1] Payment clause content.", response.getRagAnswerContext());
        assertEquals(2, response.getSteps().size());
        assertFalse(response.getFinalAnswer().contains(PRIVATE_RAG_DOC_MARKER));
        assertFalse(response.getSteps().get(1).getOutputSummary().contains(PRIVATE_RAG_DOC_MARKER));
        assertFalse(response.getRagResults().get(0).getSnippet().contains(PRIVATE_RAG_DOC_MARKER));
        verifyPersistenceSuccess();
        verify(documentRagQaTool).execute(any());
        verify(documentSummaryTool, never()).execute(any());
        verify(documentQaTool, never()).execute(any());
        verify(documentRagTool, never()).execute(any());
        assertEmptySelectorMetrics();
    }

    @Test
    void shouldReturnFriendlyRagFallbackWhenNoChunksRetrieved() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(110L);
        request.setTask("RAG retrieve missing topic evidence");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 110L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        110L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "Document text without the requested synthetic marker. " + PRIVATE_RAG_DOC_MARKER
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentRagQaTool.getToolName()).thenReturn(DocumentRagQaTool.TOOL_NAME);
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "rag_tool",
                List.of("document_status_tool", DocumentRagQaTool.TOOL_NAME),
                "rag reason",
                List.of("RAG", "retrieve")
        ));
        when(documentRagQaTool.execute(any())).thenReturn(new DocumentRagQaTool.RagQaResult(
                100L,
                110L,
                "RAG retrieve missing topic evidence",
                "\u672a\u5728\u5f53\u524d\u6587\u6863\u7d22\u5f15\u4e2d\u68c0\u7d22\u5230\u8db3\u591f\u8bc1\u636e",
                "",
                3,
                1,
                List.of(),
                List.of(),
                true,
                true,
                "no_evidence",
                "topK=3, indexVersion=1, hitCount=0, citationCount=0, noEvidence=true, fallbackUsed=true, fallbackReason=no_evidence"
        ));
        stubStatusTool();
        stubRagQaTool();
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals("rag_tool", response.getDecision());
        assertTrue(response.getFinalAnswer().contains("\u672a\u5728\u5f53\u524d\u6587\u6863\u7d22\u5f15\u4e2d\u68c0\u7d22\u5230\u8db3\u591f\u8bc1\u636e"));
        assertNotNull(response.getRagResults());
        assertTrue(response.getRagResults().isEmpty());
        assertEquals("", response.getRagAnswerContext());
        assertEquals(2, response.getSteps().size());
        assertTrue(response.getSteps().get(1).getOutputSummary().contains("fallbackUsed=true"));
        assertTrue(response.getSteps().get(1).getOutputSummary().contains("fallbackReason=no_evidence"));
        assertFalse(response.getSteps().get(1).getOutputSummary().contains(PRIVATE_RAG_DOC_MARKER));
        assertFalse(response.getFinalAnswer().contains(PRIVATE_RAG_DOC_MARKER));
        verifyPersistenceSuccess();
        verify(documentRagQaTool).execute(any());
        verify(documentSummaryTool, never()).execute(any());
        verify(documentQaTool, never()).execute(any());
        verify(documentRagTool, never()).execute(any());
        assertEmptySelectorMetrics();
    }

    @Test
    void shouldPersistFailedStepWhenRagToolThrows() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(111L);
        request.setTask("RAG retrieve unavailable evidence");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 111L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        111L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "content"
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentRagQaTool.getToolName()).thenReturn(DocumentRagQaTool.TOOL_NAME);
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "rag_tool",
                List.of("document_status_tool", DocumentRagQaTool.TOOL_NAME),
                "rag reason",
                List.of("RAG", "retrieve")
        ));
        when(documentRagQaTool.execute(any())).thenThrow(new IllegalStateException("internal endpoint should not leak"));
        stubStatusTool();
        stubRagQaTool();
        stubPersistenceTask();

        assertThrows(IllegalStateException.class, () -> service.run(100L, request));

        verify(persistenceService).createStep(
                anyLong(),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(DocumentRagQaTool.TOOL_NAME),
                anyString(),
                org.mockito.ArgumentMatchers.eq("rag qa tool failed: IllegalStateException"),
                anyLong(),
                org.mockito.ArgumentMatchers.eq("failed"),
                org.mockito.ArgumentMatchers.eq("IllegalStateException")
        );
        verify(persistenceService).updateTaskFailed(anyLong(), anyString());
    }

    @Test
    void shouldRunShadowCompareWithoutChangingRealToolExecution() {
        selectorProperties.setShadowEnabled(true);
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(104L);
        request.setTask("Please summarize this document");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 104L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        104L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "content"
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentSummaryTool.getToolName()).thenReturn("document_summary_tool");
        ToolSelector.SelectResult primarySelection = new ToolSelector.SelectResult(
                "summary_tool",
                List.of("document_status_tool", "document_summary_tool"),
                "summary reason",
                List.of("summary")
        );
        when(toolSelector.select(anyString())).thenReturn(primarySelection);
        when(toolDefinitionProvider.getAllDefinitions()).thenReturn(List.of(
                new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
                new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
                new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true)
        ));
        LlmToolSelectionResult shadowSelection = new LlmToolSelectionResult(
                "summary_tool",
                List.of("document_status_tool", "document_summary_tool"),
                "shadow summary reason",
                List.of("summary"),
                0.86d
        );
        when(shadowToolSelector.selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(shadowSelection);
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubStatusTool();
        stubSummaryTool();
        stubPersistenceTask();

        var response = service.run(100L, request);
        LlmSelectorShadowResult compare = LlmSelectorShadowResult.from(primarySelection, shadowSelection);

        assertEquals("summary_tool", response.getDecision());
        assertEquals("summary", response.getFinalAnswer());
        assertTrue(compare.matched());
        SelectorMetricsSnapshot snapshot = selectorMetricsCollector.snapshot();
        assertEquals(1L, snapshot.totalComparisons());
        assertEquals(1L, snapshot.matchedCount());
        assertEquals(0L, snapshot.mismatchCount());
        assertEquals(1.0d, snapshot.matchRate());
        verify(shadowToolSelector).selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(realShadowPromptBuilder, never()).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(documentSummaryTool).execute(any());
        verify(documentQaTool, never()).execute(any());
    }

    @Test
    void shouldNotRunShadowCompareWhenSwitchDisabled() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(105L);
        request.setTask("Please summarize this document");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 105L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        105L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "content"
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentSummaryTool.getToolName()).thenReturn("document_summary_tool");
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "summary_tool",
                List.of("document_status_tool", "document_summary_tool"),
                "summary reason",
                List.of("summary")
        ));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubStatusTool();
        stubSummaryTool();
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals("summary_tool", response.getDecision());
        verify(shadowToolSelector, never()).selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(realShadowPromptBuilder, never()).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        assertEmptySelectorMetrics();
    }

    @Test
    void shouldKeepPrimaryDecisionWhenThresholdAllowsPromotionCandidate() {
        SelectorMetricsCollector thresholdMetrics = new SelectorMetricsCollector();
        for (int i = 0; i < 20; i++) {
            thresholdMetrics.recordSuccess("fake", "summary_tool", "summary_tool");
        }
        SelectorShadowThresholdDecision thresholdDecision = new SelectorShadowThresholdPolicy()
                .evaluate(thresholdMetrics.snapshot());
        assertTrue(thresholdDecision.allowPromotionCandidate());

        selectorProperties.setShadowEnabled(true);
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(108L);
        request.setTask("Please summarize this document");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 108L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        108L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "content"
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentSummaryTool.getToolName()).thenReturn("document_summary_tool");
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "summary_tool",
                List.of("document_status_tool", "document_summary_tool"),
                "summary reason",
                List.of("summary")
        ));
        stubToolDefinitions();
        when(shadowToolSelector.selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(new LlmToolSelectionResult(
                        "qa_tool",
                        List.of("document_status_tool", "document_qa_tool"),
                        "fake shadow reason",
                        List.of("evidence"),
                        0.9d
                ));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubStatusTool();
        stubSummaryTool();
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals("summary_tool", response.getDecision());
        assertEquals("summary", response.getFinalAnswer());
        verify(documentSummaryTool).execute(any());
        verify(documentQaTool, never()).execute(any());
    }

    @Test
    void shouldKeepPrimaryFlowWhenRealShadowEnabledWithDisabledClient() {
        selectorProperties.setShadowEnabled(true);
        selectorProperties.setRealShadowEnabled(true);
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(106L);
        request.setTask("Please summarize this document");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 106L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        106L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "content"
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentSummaryTool.getToolName()).thenReturn("document_summary_tool");
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "summary_tool",
                List.of("document_status_tool", "document_summary_tool"),
                "summary reason",
                List.of("summary")
        ));
        stubToolDefinitions();
        when(shadowToolSelector.selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(new LlmToolSelectionResult(
                        "summary_tool",
                        List.of("document_status_tool", "document_summary_tool"),
                        "fake shadow reason",
                        List.of("summary"),
                        0.9d
                ));
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn("disabled real shadow prompt");
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubStatusTool();
        stubSummaryTool();
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals("summary_tool", response.getDecision());
        assertEquals("summary", response.getFinalAnswer());
        verify(realShadowPromptBuilder).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(realShadowParser, never()).parse(anyString());
        verify(documentSummaryTool).execute(any());
        verify(documentQaTool, never()).execute(any());
        SelectorMetricsSnapshot snapshot = selectorMetricsCollector.snapshot();
        assertEquals(1L, snapshot.totalComparisons());
        assertEquals(1L, snapshot.matchedCount());
    }

    @Test
    void shouldKeepPrimaryFlowWhenRealShadowThrows() {
        selectorProperties.setShadowEnabled(true);
        selectorProperties.setRealShadowEnabled(true);
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(107L);
        request.setTask("Please summarize this document");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 107L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        107L,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "summary",
                        "content"
                ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        when(documentSummaryTool.getToolName()).thenReturn("document_summary_tool");
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "summary_tool",
                List.of("document_status_tool", "document_summary_tool"),
                "summary reason",
                List.of("summary")
        ));
        stubToolDefinitions();
        when(shadowToolSelector.selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenReturn(new LlmToolSelectionResult(
                        "summary_tool",
                        List.of("document_status_tool", "document_summary_tool"),
                        "fake shadow reason",
                        List.of("summary"),
                        0.9d
                ));
        when(realShadowPromptBuilder.build(anyString(), anyBoolean(), anyBoolean(), anyList()))
                .thenThrow(new IllegalStateException("real shadow unavailable"));
        when(documentSummaryTool.execute(new DocumentSummaryTool.SummaryInput(
                "Please summarize this document",
                "summary",
                "content"
        ))).thenReturn(new DocumentSummaryTool.SummaryResult("summary", "summary_field"));
        stubStatusTool();
        stubSummaryTool();
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals("summary_tool", response.getDecision());
        assertEquals("summary", response.getFinalAnswer());
        verify(realShadowParser, never()).parse(anyString());
        verify(documentSummaryTool).execute(any());
        SelectorMetricsSnapshot snapshot = selectorMetricsCollector.snapshot();
        assertEquals(1L, snapshot.totalComparisons());
        assertEquals(1L, snapshot.matchedCount());
    }

    @Test
    void shouldReturnPendingHintWhenParseNotReady() {
        DocumentAgentServiceImpl service = buildService();

        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(103L);
        request.setTask("Please summarize this document");

        when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(100L, 103L)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        103L,
                        "demo",
                        ParseStatusConstants.PARSING,
                        false,
                        "parsing now",
                        null,
                        null
        ));
        when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
        stubStatusTool();
        stubPersistenceTask();

        var response = service.run(100L, request);

        assertEquals(1001L, response.getTaskId());
        assertEquals("status_only", response.getDecision());
        assertNotNull(response.getRoutingReason());
        assertFalse(response.getRoutingReason().isBlank());
        assertTrue(response.getMatchedKeywords().isEmpty());
        assertNull(response.getSessionId());
        assertTrue(response.getFinalAnswer().contains("PARSING"));
        verifyPersistenceSuccess();
        verify(toolSelector, never()).select(anyString());
        verify(shadowToolSelector, never()).selectWithPrompt(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(realShadowPromptBuilder, never()).build(anyString(), anyBoolean(), anyBoolean(), anyList());
        verify(documentSummaryTool, never()).execute(any());
        verify(documentQaTool, never()).execute(any());
        verify(documentRagTool, never()).execute(any());
        assertEmptySelectorMetrics();
    }

    private void stubToolDefinitions() {
        when(toolDefinitionProvider.getAllDefinitions()).thenReturn(List.of(
                new ToolDefinition("document_status_tool", "Document status", "Checks parse status.", "{}", "{}", true),
                new ToolDefinition("document_summary_tool", "Document summary", "Returns summary.", "{}", "{}", true),
                new ToolDefinition("document_qa_tool", "Document QA", "Answers with citations.", "{}", "{}", true)
        ));
    }

    private void assertEmptySelectorMetrics() {
        SelectorMetricsSnapshot snapshot = selectorMetricsCollector.snapshot();
        assertEquals(0L, snapshot.totalComparisons());
        assertEquals(0L, snapshot.matchedCount());
        assertEquals(0L, snapshot.mismatchCount());
        assertEquals(0.0d, snapshot.matchRate());
    }
}
