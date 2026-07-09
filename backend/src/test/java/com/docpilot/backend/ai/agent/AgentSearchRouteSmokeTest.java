package com.docpilot.backend.ai.agent;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.service.AgentTaskPersistenceService;
import com.docpilot.backend.ai.agent.service.ToolCallService;
import com.docpilot.backend.ai.agent.service.impl.DocumentAgentServiceImpl;
import com.docpilot.backend.ai.agent.tool.DocumentQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentSearchTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.DocumentSummaryTool;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionParser;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionPromptBuilder;
import com.docpilot.backend.ai.agent.tool.LlmToolSelector;
import com.docpilot.backend.ai.agent.tool.SelectorMetricsCollector;
import com.docpilot.backend.ai.agent.tool.ToolDefinitionProvider;
import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import com.docpilot.backend.ai.agent.tool.ToolSelector;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.rag.RagEvidenceCitation;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSearchRouteSmokeTest {

    private static final Long USER_ID = 100L;
    private static final Long DOCUMENT_ID = 111L;
    private static final String RAW_DOCUMENT_MARKER = "PRIVATE_AGENT_SEARCH_ROUTE_RAW_DOCUMENT_MARKER";

    @Mock
    private DocumentStatusTool documentStatusTool;

    @Mock
    private DocumentSummaryTool documentSummaryTool;

    @Mock
    private DocumentQaTool documentQaTool;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolCallService toolCallService;

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

    @Test
    void shouldWriteRedactedArtifactWhenEnabled() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DOCPILOT_AGENT_SEARCH_ROUTE_SMOKE_ENABLED")));
        String artifactPath = System.getenv("DOCPILOT_AGENT_SEARCH_ROUTE_SMOKE_ARTIFACT");
        assumeTrue(artifactPath != null && !artifactPath.isBlank());

        String marker = System.getenv("DOCPILOT_AGENT_SEARCH_ROUTE_SMOKE_MARKER");
        if (marker == null || marker.isBlank()) {
            marker = "docpilot-agent-search-route-local";
        }

        List<Map<String, Object>> caseResults = new ArrayList<>();
        List<String> failureBuckets = new ArrayList<>();

        RouteCaseResult searchCase = runSearchRouteCase();
        RouteCaseResult ragCase = runRagRouteCase();
        caseResults.add(searchCase.toArtifactMap());
        caseResults.add(ragCase.toArtifactMap());

        boolean searchDecisionPass = searchCase.passed();
        boolean ragDecisionPass = ragCase.passed();
        boolean redactionPass = searchCase.redactionPass() && ragCase.redactionPass();
        if (!searchDecisionPass) {
            failureBuckets.add("searchDecisionMismatch");
        }
        if (!ragDecisionPass) {
            failureBuckets.add("ragDecisionMismatch");
        }
        if (!redactionPass) {
            failureBuckets.add("redactionFailed");
        }

        String status = failureBuckets.isEmpty() ? "PASS" : "REVIEW";
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("smokeMarker", marker);
        artifact.put("status", status);
        artifact.put("agentSearchRoute", Map.of(
                "status", status,
                "passed", failureBuckets.isEmpty(),
                "caseCount", caseResults.size(),
                "searchDecisionPass", searchDecisionPass,
                "ragDecisionPass", ragDecisionPass,
                "redactionPass", redactionPass,
                "searchToolName", DocumentSearchTool.TOOL_NAME,
                "ragToolName", DocumentRagQaTool.TOOL_NAME,
                "failureBuckets", failureBuckets
        ));
        artifact.put("caseResults", caseResults);
        artifact.put("rawTaskStored", false);
        artifact.put("rawAnswerStored", false);
        artifact.put("rawDocumentStored", false);
        artifact.put("rawEvidenceStored", false);

        Path path = Path.of(artifactPath);
        Files.createDirectories(path.getParent());
        new ObjectMapper().findAndRegisterModules()
                .writerWithDefaultPrettyPrinter()
                .writeValue(path.toFile(), artifact);
    }

    private RouteCaseResult runSearchRouteCase() {
        DocumentAgentServiceImpl service = buildService();
        stubReadyDocumentStatus();
        stubTaskPersistence();
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "search_tool",
                List.of("document_status_tool", DocumentSearchTool.TOOL_NAME),
                "retrieval only",
                List.of("retrieve", "topK")
        ));
        stubToolCallService();

        DocumentAgentRequest request = request("retrieve topK chunks and show similarity score");
        request.setTopK(4);
        request.setIndexVersion(2);

        var response = service.run(USER_ID, request);
        boolean decisionPass = "search_tool".equals(response.getFinalDecision())
                && response.getFinalAnswer().contains("Retrieved 1 evidence chunk")
                && response.getSteps().stream().anyMatch(step -> DocumentSearchTool.TOOL_NAME.equals(step.getToolName()));
        boolean redactionPass = doesNotLeakRawMarker(response.getFinalAnswer())
                && doesNotLeakRawMarker(response.getRagAnswerContext())
                && response.getSteps().stream().allMatch(step ->
                doesNotLeakRawMarker(step.getInputSummary())
                        && doesNotLeakRawMarker(step.getOutputSummary())
                        && doesNotLeakRawMarker(step.getErrorMessage()));

        verify(documentQaTool, never()).execute(any());
        verify(documentSummaryTool, never()).execute(any());
        return new RouteCaseResult(
                "agent-search-retrieval-route",
                "search_tool",
                response.getFinalDecision(),
                DocumentSearchTool.TOOL_NAME,
                decisionPass,
                redactionPass,
                response.getSteps().size()
        );
    }

    private RouteCaseResult runRagRouteCase() {
        DocumentAgentServiceImpl service = buildService();
        stubReadyDocumentStatus();
        stubTaskPersistence();
        when(toolSelector.select(anyString())).thenReturn(new ToolSelector.SelectResult(
                "rag_tool",
                List.of("document_status_tool", DocumentRagQaTool.TOOL_NAME),
                "answer with evidence",
                List.of("answer", "evidence")
        ));
        stubToolCallService();

        DocumentAgentRequest request = request("answer with evidence");
        request.setTopK(3);

        var response = service.run(USER_ID, request);
        boolean decisionPass = "rag_tool".equals(response.getFinalDecision())
                && "grounded answer from safe evidence".equals(response.getFinalAnswer())
                && response.getSteps().stream().anyMatch(step -> DocumentRagQaTool.TOOL_NAME.equals(step.getToolName()));
        boolean redactionPass = doesNotLeakRawMarker(response.getFinalAnswer())
                && doesNotLeakRawMarker(response.getRagAnswerContext())
                && response.getSteps().stream().allMatch(step ->
                doesNotLeakRawMarker(step.getInputSummary())
                        && doesNotLeakRawMarker(step.getOutputSummary())
                        && doesNotLeakRawMarker(step.getErrorMessage()));

        verify(documentQaTool, never()).execute(any());
        verify(documentSummaryTool, never()).execute(any());
        return new RouteCaseResult(
                "agent-rag-answer-route",
                "rag_tool",
                response.getFinalDecision(),
                DocumentRagQaTool.TOOL_NAME,
                decisionPass,
                redactionPass,
                response.getSteps().size()
        );
    }

    private DocumentAgentServiceImpl buildService() {
        return new DocumentAgentServiceImpl(
                toolRegistry,
                toolCallService,
                toolSelector,
                persistenceService,
                new AgentSelectorProperties(),
                shadowToolSelector,
                toolDefinitionProvider,
                new SelectorMetricsCollector(),
                realShadowPromptBuilder,
                realShadowParser
        );
    }

    private void stubReadyDocumentStatus() {
        lenient().when(documentStatusTool.execute(new DocumentStatusTool.StatusInput(USER_ID, DOCUMENT_ID)))
                .thenReturn(new DocumentStatusTool.StatusResult(
                        DOCUMENT_ID,
                        "demo",
                        ParseStatusConstants.SUCCESS,
                        true,
                        "ready",
                        "safe summary",
                        "raw body " + RAW_DOCUMENT_MARKER
                ));
        lenient().when(documentStatusTool.getToolName()).thenReturn("document_status_tool");
    }

    private void stubTaskPersistence() {
        when(persistenceService.createTask(anyLong(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> {
                    AgentTask task = new AgentTask();
                    task.setId(1001L);
                    task.setUserId(invocation.getArgument(0));
                    task.setDocumentId(invocation.getArgument(1));
                    task.setTaskInput(invocation.getArgument(2));
                    task.setCreateTime(LocalDateTime.now());
                    return task;
                });
    }

    private void stubToolCallService() {
        lenient().when(toolCallService.call(anyLong(), org.mockito.ArgumentMatchers.argThat(request ->
                request != null && "document_status_tool".equals(request.getToolName())
        ))).thenAnswer(invocation -> {
            ToolCallRequest request = invocation.getArgument(1);
            Long documentId = ((Number) request.getArguments().get("documentId")).longValue();
            DocumentStatusTool.StatusResult result = documentStatusTool.execute(
                    new DocumentStatusTool.StatusInput(USER_ID, documentId)
            );
            return ToolCallResult.success(
                    "document_status_tool",
                    result,
                    "parseStatus=" + result.parseStatus() + ", parseReady=" + result.parseReady()
            );
        });
        lenient().when(toolCallService.call(anyLong(), org.mockito.ArgumentMatchers.argThat(request ->
                request != null && DocumentSearchTool.TOOL_NAME.equals(request.getToolName())
        ))).thenAnswer(invocation -> {
            ToolCallRequest request = invocation.getArgument(1);
            Long documentId = ((Number) request.getArguments().get("documentId")).longValue();
            int topK = ((Number) request.getArguments().getOrDefault("topK", 3)).intValue();
            int indexVersion = ((Number) request.getArguments().getOrDefault("indexVersion", 1)).intValue();
            DocumentSearchTool.SearchCitation citation = new DocumentSearchTool.SearchCitation(
                    1,
                    documentId,
                    "Doc",
                    indexVersion,
                    901L,
                    0,
                    2,
                    "Payment",
                    "page=2#block=1",
                    "PARAGRAPH",
                    "Payment clause quote",
                    "Payment clause snippet",
                    "hash",
                    0.91d
            );
            DocumentSearchTool.SearchHit hit = new DocumentSearchTool.SearchHit(
                    1,
                    0.91d,
                    "Doc",
                    901L,
                    0,
                    2,
                    "Payment",
                    "page=2#block=1",
                    "PARAGRAPH",
                    "Payment clause quote",
                    "Payment clause snippet",
                    "hash"
            );
            DocumentSearchTool.SearchResult result = new DocumentSearchTool.SearchResult(
                    USER_ID,
                    documentId,
                    "",
                    topK,
                    indexVersion,
                    false,
                    1,
                    1,
                    List.of(hit),
                    List.of(citation),
                    "topK=" + topK + ", indexVersion=" + indexVersion + ", hitCount=1, citationCount=1, noEvidence=false"
            );
            return ToolCallResult.success(
                    DocumentSearchTool.TOOL_NAME,
                    result,
                    result.outputSummary(),
                    0L,
                    result.citations(),
                    result.hits()
            );
        });
        lenient().when(toolCallService.call(anyLong(), org.mockito.ArgumentMatchers.argThat(request ->
                request != null && DocumentRagQaTool.TOOL_NAME.equals(request.getToolName())
        ))).thenAnswer(invocation -> {
            ToolCallRequest request = invocation.getArgument(1);
            Long documentId = ((Number) request.getArguments().get("documentId")).longValue();
            int topK = ((Number) request.getArguments().getOrDefault("topK", 3)).intValue();
            int indexVersion = ((Number) request.getArguments().getOrDefault("indexVersion", 1)).intValue();
            RagRetrievalHit hit = new RagRetrievalHit(
                    1,
                    "vector-1",
                    0.92d,
                    USER_ID,
                    documentId,
                    "Doc",
                    indexVersion,
                    902L,
                    0,
                    "Safe evidence snippet",
                    "hash-rag",
                    0,
                    22,
                    4,
                    "mock",
                    "Payment",
                    "PARAGRAPH",
                    1,
                    "page=1#block=1",
                    "PARAGRAPH"
            );
            RagEvidenceCitation citation = hit.toCitation();
            DocumentRagQaTool.RagQaResult result = new DocumentRagQaTool.RagQaResult(
                    USER_ID,
                    documentId,
                    "",
                    "grounded answer from safe evidence",
                    "",
                    topK,
                    indexVersion,
                    List.of(hit),
                    List.of(citation),
                    false,
                    false,
                    "",
                    "topK=" + topK + ", indexVersion=" + indexVersion + ", hitCount=1, citationCount=1, noEvidence=false"
            );
            return ToolCallResult.success(
                    DocumentRagQaTool.TOOL_NAME,
                    result,
                    result.outputSummary(),
                    0L,
                    result.citations(),
                    result.retrievalHits()
            );
        });
    }

    private DocumentAgentRequest request(String task) {
        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(DOCUMENT_ID);
        request.setTask(task);
        return request;
    }

    private boolean doesNotLeakRawMarker(String value) {
        return value == null || !value.contains(RAW_DOCUMENT_MARKER);
    }

    private record RouteCaseResult(String caseId,
                                   String expectedDecision,
                                   String actualDecision,
                                   String selectedToolName,
                                   boolean decisionPass,
                                   boolean redactionPass,
                                   int stepCount) {

        boolean passed() {
            return decisionPass && redactionPass;
        }

        Map<String, Object> toArtifactMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("caseId", caseId);
            map.put("expectedDecision", expectedDecision);
            map.put("actualDecision", actualDecision);
            map.put("selectedToolName", selectedToolName);
            map.put("decisionPass", decisionPass);
            map.put("redactionPass", redactionPass);
            map.put("stepCount", stepCount);
            return map;
        }
    }
}
