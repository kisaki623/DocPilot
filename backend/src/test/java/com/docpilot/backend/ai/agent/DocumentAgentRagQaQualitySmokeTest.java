package com.docpilot.backend.ai.agent;

import com.docpilot.backend.ai.agent.config.AgentSelectorProperties;
import com.docpilot.backend.ai.agent.dto.DocumentAgentRequest;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import com.docpilot.backend.ai.agent.service.AgentTaskPersistenceService;
import com.docpilot.backend.ai.agent.service.impl.DocumentAgentServiceImpl;
import com.docpilot.backend.ai.agent.tool.DocumentRagQaTool;
import com.docpilot.backend.ai.agent.tool.DocumentStatusTool;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionParser;
import com.docpilot.backend.ai.agent.tool.LlmToolSelectionPromptBuilder;
import com.docpilot.backend.ai.agent.tool.LlmToolSelector;
import com.docpilot.backend.ai.agent.tool.SelectorMetricsCollector;
import com.docpilot.backend.ai.agent.tool.ToolDefinitionProvider;
import com.docpilot.backend.ai.agent.tool.ToolRegistry;
import com.docpilot.backend.ai.agent.tool.ToolSelector;
import com.docpilot.backend.ai.agent.vo.DocumentAgentResponse;
import com.docpilot.backend.ai.rag.RagEvidenceCitation;
import com.docpilot.backend.ai.rag.RagQaAnswer;
import com.docpilot.backend.ai.rag.RagQaQuery;
import com.docpilot.backend.ai.rag.RagRetrievalHit;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.service.RagQaService;
import com.docpilot.backend.common.constant.ParseStatusConstants;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentAgentRagQaQualitySmokeTest {

    private static final String PRIVATE_AGENT_MARKER = "PRIVATE_AGENT_T007_DOC_MARKER_DO_NOT_DUMP";

    @Test
    void shouldExposeRagQaCitationsAndRetrievalHitsInAgentTrace() {
        StubRagQaService ragQaService = new StubRagQaService();
        RagRetrievalHit hit = hit();
        RagEvidenceCitation citation = hit.toCitation();
        ragQaService.answer = new RagQaAnswer(
                100L,
                9001L,
                "根据文档证据说明 Redis cache 的作用",
                "Redis cache stores hot session context. [1]",
                "agent-rag-hit",
                new RagRetrievalResult(
                        100L,
                        9001L,
                        "根据文档证据说明 Redis cache 的作用",
                        4,
                        2,
                        List.of(hit),
                        List.of(citation),
                        false,
                        "in_memory",
                        "",
                        "mock-t007"
                ),
                false,
                false,
                ""
        );
        Harness harness = Harness.create(ragQaService);
        DocumentAgentRequest request = request("根据文档证据说明 Redis cache 的作用", "agent-rag-hit");
        request.setTopK(4);
        request.setIndexVersion(2);

        DocumentAgentResponse response = harness.service.run(100L, request);

        assertThat(ragQaService.lastQuery.topK()).isEqualTo(4);
        assertThat(ragQaService.lastQuery.indexVersion()).isEqualTo(2);
        assertThat(response.getDecision()).isEqualTo("rag_tool");
        assertThat(response.getFinalAnswer()).contains("[1]");
        assertThat(response.getRagResults()).hasSize(1);
        assertThat(response.getRagResults().get(0).getChunkId()).isEqualTo(501L);
        assertThat(response.getRagResults().get(0).getMetadata())
                .containsEntry("userId", "100")
                .containsEntry("documentId", "9001")
                .containsEntry("indexVersion", "2")
                .containsEntry("embeddingModel", "mock-t007");
        assertThat(response.getRagCitations()).containsExactly(citation);
        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getRagAnswerContext()).isEqualTo("[1] Redis cache stores hot session context.");
        assertThat(response.getSteps()).hasSize(2);
        assertThat(response.getSteps().get(1).getToolName()).isEqualTo(DocumentRagQaTool.TOOL_NAME);
        assertThat(response.getSteps().get(1).getOutputSummary())
                .contains("hitCount=1")
                .contains("citationCount=1")
                .contains("noEvidence=false")
                .doesNotContain(PRIVATE_AGENT_MARKER);
        verify(harness.persistenceService, atLeastOnce()).createStep(
                anyLong(),
                any(Integer.class),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyString(),
                anyString()
        );
    }

    @Test
    void shouldExposeNoEvidenceFallbackInAgentTrace() {
        StubRagQaService ragQaService = new StubRagQaService();
        ragQaService.answer = new RagQaAnswer(
                100L,
                9001L,
                "RAG 检索缺失主题证据",
                "未在当前文档索引中检索到足够证据，无法基于文档回答该问题。",
                "agent-rag-empty",
                new RagRetrievalResult(
                        100L,
                        9001L,
                        "RAG 检索缺失主题证据",
                        3,
                        1,
                        List.of(),
                        List.of(),
                        true,
                        "in_memory",
                        "",
                        "mock-t007"
                ),
                true,
                true,
                "no_evidence"
        );
        Harness harness = Harness.create(ragQaService);

        DocumentAgentResponse response = harness.service.run(100L, request("RAG 检索缺失主题证据", "agent-rag-empty"));

        assertThat(response.getDecision()).isEqualTo("rag_tool");
        assertThat(response.getFinalAnswer()).contains("未在当前文档索引中检索到足够证据");
        assertThat(response.getRagResults()).isEmpty();
        assertThat(response.getRagCitations()).isEmpty();
        assertThat(response.getRagAnswerContext()).isEmpty();
        assertThat(response.getSteps()).hasSize(2);
        assertThat(response.getSteps().get(1).getOutputSummary())
                .contains("hitCount=0")
                .contains("citationCount=0")
                .contains("noEvidence=true")
                .contains("fallbackUsed=true")
                .contains("fallbackReason=no_evidence")
                .doesNotContain(PRIVATE_AGENT_MARKER);
        verify(harness.persistenceService).updateTaskSuccess(anyLong(), anyString(), anyString(), anyLong());
    }

    private static DocumentAgentRequest request(String task, String sessionId) {
        DocumentAgentRequest request = new DocumentAgentRequest();
        request.setDocumentId(9001L);
        request.setTask(task);
        request.setSessionId(sessionId);
        return request;
    }

    private static RagRetrievalHit hit() {
        return new RagRetrievalHit(
                1,
                "vec-t007-1",
                0.93D,
                100L,
                9001L,
                2,
                501L,
                0,
                "Redis cache stores hot session context.",
                "hash-t007",
                0,
                39,
                6,
                "mock-t007"
        );
    }

    private static final class Harness {

        private final DocumentAgentServiceImpl service;
        private final AgentTaskPersistenceService persistenceService;

        private Harness(DocumentAgentServiceImpl service, AgentTaskPersistenceService persistenceService) {
            this.service = service;
            this.persistenceService = persistenceService;
        }

        private static Harness create(StubRagQaService ragQaService) {
            DocumentStatusTool statusTool = mock(DocumentStatusTool.class);
            when(statusTool.getToolName()).thenReturn("document_status_tool");
            when(statusTool.execute(any())).thenReturn(new DocumentStatusTool.StatusResult(
                    9001L,
                    "T007 smoke document",
                    ParseStatusConstants.SUCCESS,
                    true,
                    "ready",
                    "summary",
                    "RAG smoke content " + PRIVATE_AGENT_MARKER
            ));
            ToolRegistry registry = new ToolRegistry(List.of(statusTool, new DocumentRagQaTool(ragQaService)));
            ToolSelector selector = task -> new ToolSelector.SelectResult(
                    "rag_tool",
                    List.of("document_status_tool", DocumentRagQaTool.TOOL_NAME),
                    "T007 smoke routes evidence request to rag_qa_tool",
                    List.of("RAG", "证据")
            );
            AgentTaskPersistenceService persistenceService = mock(AgentTaskPersistenceService.class);
            AgentTask task = new AgentTask();
            task.setId(777L);
            when(persistenceService.createTask(anyLong(), anyLong(), anyString(), any())).thenReturn(task);
            DocumentAgentServiceImpl service = new DocumentAgentServiceImpl(
                    registry,
                    selector,
                    persistenceService,
                    new AgentSelectorProperties(),
                    mock(LlmToolSelector.class),
                    mock(ToolDefinitionProvider.class),
                    new SelectorMetricsCollector(),
                    mock(LlmToolSelectionPromptBuilder.class),
                    mock(LlmToolSelectionParser.class)
            );
            return new Harness(service, persistenceService);
        }
    }

    private static final class StubRagQaService implements RagQaService {

        private RagQaAnswer answer;
        private RagQaQuery lastQuery;

        @Override
        public RagQaAnswer answer(RagQaQuery query) {
            this.lastQuery = query;
            return answer;
        }

        @Override
        public SseEmitter streamAnswer(RagQaQuery query) {
            throw new UnsupportedOperationException("SSE is not used in T007 agent smoke");
        }
    }
}
