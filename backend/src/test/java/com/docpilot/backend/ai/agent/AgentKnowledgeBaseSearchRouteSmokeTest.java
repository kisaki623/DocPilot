package com.docpilot.backend.ai.agent;

import com.docpilot.backend.ai.agent.dto.KnowledgeBaseAgentRequest;
import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.service.ToolCallService;
import com.docpilot.backend.ai.agent.service.impl.KnowledgeBaseAgentServiceImpl;
import com.docpilot.backend.ai.agent.tool.DocumentToolSelector;
import com.docpilot.backend.ai.agent.tool.KnowledgeBaseSearchTool;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalHit;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.service.KnowledgeBaseRagQaService;
import com.docpilot.backend.common.error.ErrorCode;
import com.docpilot.backend.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentKnowledgeBaseSearchRouteSmokeTest {

    private static final Long USER_ID = 7L;
    private static final Long KNOWLEDGE_BASE_ID = 99L;
    private static final String RAW_KB_MARKER = "PRIVATE_KB_AGENT_ROUTE_RAW_DOCUMENT_MARKER";

    @Test
    void shouldWriteRedactedArtifactWhenEnabled() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ENABLED")));
        String artifactPath = System.getenv("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_ARTIFACT");
        assumeTrue(artifactPath != null && !artifactPath.isBlank());

        String marker = System.getenv("DOCPILOT_AGENT_KB_SEARCH_ROUTE_SMOKE_MARKER");
        if (marker == null || marker.isBlank()) {
            marker = "docpilot-agent-kb-search-route-local";
        }

        List<Map<String, Object>> caseResults = new ArrayList<>();
        List<String> failureBuckets = new ArrayList<>();

        RouteCaseResult searchCase = runSearchRouteCase();
        RouteCaseResult answerCase = runAnswerRouteCase();
        RouteCaseResult scopeCase = runScopeFailureCase();
        caseResults.add(searchCase.toArtifactMap());
        caseResults.add(answerCase.toArtifactMap());
        caseResults.add(scopeCase.toArtifactMap());

        boolean searchDecisionPass = searchCase.passed();
        boolean answerDecisionPass = answerCase.passed();
        boolean scopeFailurePass = scopeCase.passed();
        boolean redactionPass = searchCase.redactionPass()
                && answerCase.redactionPass()
                && scopeCase.redactionPass();
        if (!searchDecisionPass) {
            failureBuckets.add("kbSearchDecisionMismatch");
        }
        if (!answerDecisionPass) {
            failureBuckets.add("kbAnswerDecisionMismatch");
        }
        if (!scopeFailurePass) {
            failureBuckets.add("kbScopeFailureNotPropagated");
        }
        if (!redactionPass) {
            failureBuckets.add("redactionFailed");
        }

        String status = failureBuckets.isEmpty() ? "PASS" : "REVIEW";
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("smokeMarker", marker);
        artifact.put("status", status);
        artifact.put("agentKbSearchRoute", Map.of(
                "status", status,
                "passed", failureBuckets.isEmpty(),
                "caseCount", caseResults.size(),
                "searchDecisionPass", searchDecisionPass,
                "answerDecisionPass", answerDecisionPass,
                "scopeFailurePass", scopeFailurePass,
                "redactionPass", redactionPass,
                "searchToolName", KnowledgeBaseSearchTool.TOOL_NAME,
                "answerStepName", "knowledge_base_rag_qa",
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
        ToolCallService toolCallService = mock(ToolCallService.class);
        KnowledgeBaseRagQaService qaService = mock(KnowledgeBaseRagQaService.class);
        KnowledgeBaseAgentServiceImpl service = new KnowledgeBaseAgentServiceImpl(
                toolCallService,
                new DocumentToolSelector(),
                qaService
        );
        KnowledgeBaseAgentRequest request = request("retrieve topK evidence chunks and show similarity score");
        request.setTopK(4);
        request.setIndexVersion(2);
        KnowledgeBaseSearchTool.SearchResult searchResult = searchResult();
        when(toolCallService.call(eq(USER_ID), any(ToolCallRequest.class))).thenReturn(ToolCallResult.success(
                KnowledgeBaseSearchTool.TOOL_NAME,
                searchResult,
                searchResult.outputSummary(),
                0L,
                searchResult.citations(),
                searchResult.hits()
        ));

        var response = service.run(USER_ID, KNOWLEDGE_BASE_ID, request);
        boolean decisionPass = response.isSuccess()
                && "search_tool".equals(response.getDecision())
                && response.getSteps().stream().anyMatch(step -> KnowledgeBaseSearchTool.TOOL_NAME.equals(step.getToolName()))
                && response.getFinalAnswer().contains("检索到 1 个证据片段");
        boolean redactionPass = doesNotLeakRawMarker(response.getFinalAnswer())
                && response.getSteps().stream().allMatch(step ->
                doesNotLeakRawMarker(step.getInputSummary())
                        && doesNotLeakRawMarker(step.getOutputSummary())
                        && doesNotLeakRawMarker(step.getErrorMessage()));
        verify(qaService, never()).answer(any());
        return new RouteCaseResult(
                "agent-kb-search-route",
                "search_tool",
                response.getDecision(),
                KnowledgeBaseSearchTool.TOOL_NAME,
                decisionPass,
                redactionPass,
                response.getSteps().size(),
                ""
        );
    }

    private RouteCaseResult runAnswerRouteCase() {
        ToolCallService toolCallService = mock(ToolCallService.class);
        KnowledgeBaseRagQaService qaService = mock(KnowledgeBaseRagQaService.class);
        KnowledgeBaseAgentServiceImpl service = new KnowledgeBaseAgentServiceImpl(
                toolCallService,
                new DocumentToolSelector(),
                qaService
        );
        KnowledgeBaseAgentRequest request = request("answer with evidence from the knowledge base");
        when(qaService.answer(any(KnowledgeBaseRagQaQuery.class))).thenReturn(qaAnswer());

        var response = service.run(USER_ID, KNOWLEDGE_BASE_ID, request);
        boolean decisionPass = response.isSuccess()
                && "rag_tool".equals(response.getDecision())
                && response.getCitations().size() == 1
                && response.getSteps().stream().anyMatch(step -> "knowledge_base_rag_qa".equals(step.getToolName()));
        boolean redactionPass = response.getSteps().stream().allMatch(step ->
                doesNotLeakRawMarker(step.getInputSummary())
                        && doesNotLeakRawMarker(step.getOutputSummary())
                        && doesNotLeakRawMarker(step.getErrorMessage()));
        verify(toolCallService, never()).call(any(), any());
        verify(qaService).answer(any(KnowledgeBaseRagQaQuery.class));
        return new RouteCaseResult(
                "agent-kb-answer-route",
                "rag_tool",
                response.getDecision(),
                "knowledge_base_rag_qa",
                decisionPass,
                redactionPass,
                response.getSteps().size(),
                ""
        );
    }

    private RouteCaseResult runScopeFailureCase() {
        ToolCallService toolCallService = mock(ToolCallService.class);
        KnowledgeBaseRagQaService qaService = mock(KnowledgeBaseRagQaService.class);
        KnowledgeBaseAgentServiceImpl service = new KnowledgeBaseAgentServiceImpl(
                toolCallService,
                new DocumentToolSelector(),
                qaService
        );
        KnowledgeBaseAgentRequest request = request("retrieve evidence chunks");
        when(toolCallService.call(eq(USER_ID), any(ToolCallRequest.class))).thenReturn(ToolCallResult.failed(
                KnowledgeBaseSearchTool.TOOL_NAME,
                ErrorCode.KNOWLEDGE_BASE_FORBIDDEN.name(),
                ErrorCode.KNOWLEDGE_BASE_FORBIDDEN.name()
        ));

        String errorType = "";
        boolean decisionPass = false;
        try {
            service.run(USER_ID, KNOWLEDGE_BASE_ID, request);
        } catch (BusinessException ex) {
            errorType = ex.getErrorCode().name();
            decisionPass = ErrorCode.KNOWLEDGE_BASE_FORBIDDEN.equals(ex.getErrorCode());
        }
        return new RouteCaseResult(
                "agent-kb-scope-failure",
                "security_failure",
                "search_tool",
                KnowledgeBaseSearchTool.TOOL_NAME,
                decisionPass,
                doesNotLeakRawMarker(errorType),
                1,
                errorType
        );
    }

    private KnowledgeBaseAgentRequest request(String task) {
        KnowledgeBaseAgentRequest request = new KnowledgeBaseAgentRequest();
        request.setTask(task);
        return request;
    }

    private KnowledgeBaseSearchTool.SearchResult searchResult() {
        KnowledgeBaseSearchTool.SearchHit hit = new KnowledgeBaseSearchTool.SearchHit(
                1,
                0.91d,
                201L,
                "Doc A",
                301L,
                0,
                "Safe quote",
                "Safe snippet",
                "hash",
                0.91d,
                0.42d,
                0.88d,
                0.93d
        );
        KnowledgeBaseSearchTool.SearchCitation citation = new KnowledgeBaseSearchTool.SearchCitation(
                1,
                KNOWLEDGE_BASE_ID,
                201L,
                "Doc A",
                2,
                301L,
                0,
                "Safe quote",
                "Safe snippet",
                "hash",
                0.91d,
                0.91d,
                0.42d,
                0.88d,
                0.93d
        );
        return new KnowledgeBaseSearchTool.SearchResult(
                USER_ID,
                KNOWLEDGE_BASE_ID,
                "",
                4,
                2,
                List.of(201L, 202L),
                Map.of(201L, 1, 202L, 0),
                false,
                1,
                1,
                "hybrid",
                true,
                "rerank-model",
                true,
                3,
                8,
                List.of(hit),
                List.of(citation),
                "topK=4, indexVersion=2, documentCount=2, hitCount=1, citationCount=1, noEvidence=false"
        );
    }

    private KnowledgeBaseRagQaAnswer qaAnswer() {
        KnowledgeBaseRagRetrievalHit hit = new KnowledgeBaseRagRetrievalHit(
                1,
                KNOWLEDGE_BASE_ID,
                "vector-1",
                0.91d,
                USER_ID,
                201L,
                "Doc A",
                2,
                301L,
                0,
                "Safe quote",
                "hash",
                0,
                10,
                2,
                "embedding",
                0.91d,
                0.42d,
                0.88d,
                0.93d
        );
        KnowledgeBaseRagRetrievalResult retrieval = new KnowledgeBaseRagRetrievalResult(
                USER_ID,
                KNOWLEDGE_BASE_ID,
                "",
                4,
                2,
                List.of(201L, 202L),
                List.of(hit),
                List.of(hit.toCitation()),
                false,
                "mock",
                "collection",
                "embedding",
                Map.of(201L, 1, 202L, 0),
                "hybrid",
                true,
                "rerank-model",
                true,
                3,
                8
        );
        return new KnowledgeBaseRagQaAnswer(
                USER_ID,
                KNOWLEDGE_BASE_ID,
                "",
                "Safe grounded answer",
                "",
                retrieval,
                false,
                false,
                "",
                "mock",
                "mock-kb",
                1
        );
    }

    private boolean doesNotLeakRawMarker(String value) {
        return value == null || !value.contains(RAW_KB_MARKER);
    }

    private record RouteCaseResult(String caseId,
                                   String expectedDecision,
                                   String actualDecision,
                                   String selectedToolName,
                                   boolean decisionPass,
                                   boolean redactionPass,
                                   int stepCount,
                                   String safeErrorType) {

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
            if (safeErrorType != null && !safeErrorType.isBlank()) {
                map.put("safeErrorType", safeErrorType);
            }
            return map;
        }
    }
}
