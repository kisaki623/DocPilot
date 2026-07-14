package com.docpilot.backend.ai.agent.service;

import com.docpilot.backend.ai.agent.dto.KnowledgeBaseAgentRequest;
import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseAgentServiceImplTest {

    private static final String RAW_DOCUMENT_MARKER = "PRIVATE_KB_AGENT_RAW_DOCUMENT_MARKER";

    @Mock
    private ToolCallService toolCallService;

    @Mock
    private KnowledgeBaseRagQaService knowledgeBaseRagQaService;

    @Test
    void shouldRunKnowledgeBaseSearchToolForRetrievalOnlyIntent() {
        KnowledgeBaseAgentServiceImpl service = service();
        KnowledgeBaseAgentRequest request = request("retrieve topK evidence chunks and show source list");
        request.setTopK(4);
        request.setIndexVersion(2);
        request.setMultiQueryEnabled(true);
        request.setMaxQueryVariants(3);
        KnowledgeBaseSearchTool.SearchResult searchResult = searchResult(false);
        when(toolCallService.call(eq(7L), any(ToolCallRequest.class))).thenReturn(ToolCallResult.success(
                KnowledgeBaseSearchTool.TOOL_NAME,
                searchResult,
                searchResult.outputSummary(),
                12L,
                searchResult.citations(),
                searchResult.hits()
        ));

        var response = service.run(7L, 99L, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDecision()).isEqualTo("search_tool");
        assertThat(response.getFinalAnswer())
                .contains("检索到 1 个证据片段")
                .contains("documentHitCounts")
                .contains("retrievalMode=hybrid")
                .doesNotContain(RAW_DOCUMENT_MARKER);
        assertThat(response.getDocumentHitCounts()).containsEntry(201L, 1);
        assertThat(response.getRetrievalMode()).isEqualTo("hybrid");
        assertThat(response.isMultiQueryApplied()).isTrue();
        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getSteps()).hasSize(1);
        assertThat(response.getSteps().get(0).getToolName()).isEqualTo(KnowledgeBaseSearchTool.TOOL_NAME);
        assertThat(response.getSteps().get(0).getInputSummary()).doesNotContain(RAW_DOCUMENT_MARKER);
        assertThat(response.getSteps().get(0).getOutputSummary()).doesNotContain(RAW_DOCUMENT_MARKER);

        ArgumentCaptor<ToolCallRequest> captor = ArgumentCaptor.forClass(ToolCallRequest.class);
        verify(toolCallService).call(eq(7L), captor.capture());
        verify(knowledgeBaseRagQaService, never()).answer(any());
        ToolCallRequest toolRequest = captor.getValue();
        assertThat(toolRequest.getToolName()).isEqualTo(KnowledgeBaseSearchTool.TOOL_NAME);
        assertThat(toolRequest.getArguments())
                .containsEntry("knowledgeBaseId", 99L)
                .containsEntry("query", request.getTask())
                .containsEntry("topK", 4)
                .containsEntry("indexVersion", 2)
                .containsEntry("multiQueryEnabled", true)
                .containsEntry("maxQueryVariants", 3);
    }

    @Test
    void shouldReturnNoEvidenceSummary() {
        KnowledgeBaseAgentServiceImpl service = service();
        KnowledgeBaseAgentRequest request = request("retrieve evidence chunks");
        KnowledgeBaseSearchTool.SearchResult searchResult = new KnowledgeBaseSearchTool.SearchResult(
                7L,
                99L,
                "",
                3,
                1,
                List.of(201L, 202L),
                Map.of(),
                true,
                0,
                0,
                "vector",
                false,
                "",
                false,
                0,
                0,
                List.of(),
                List.of(),
                "topK=3, indexVersion=1, documentCount=2, hitCount=0, citationCount=0, noEvidence=true"
        );
        when(toolCallService.call(eq(7L), any(ToolCallRequest.class))).thenReturn(ToolCallResult.success(
                KnowledgeBaseSearchTool.TOOL_NAME,
                searchResult,
                searchResult.outputSummary(),
                0L,
                List.of(),
                List.of()
        ));

        var response = service.run(7L, 99L, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getFinalAnswer()).contains("未在当前知识库索引中检索到足够证据");
        assertThat(response.getCitations()).isEmpty();
        assertThat(response.getDocumentHitCounts()).isEmpty();
    }

    @Test
    void shouldRunKnowledgeBaseRagQaForAnswerIntent() {
        KnowledgeBaseAgentServiceImpl service = service();
        KnowledgeBaseAgentRequest request = request("answer with evidence");
        request.setTopK(5);
        request.setIndexVersion(3);
        request.setSessionId("session-a");
        request.setMultiQueryEnabled(true);
        request.setMaxQueryVariants(4);
        when(knowledgeBaseRagQaService.answer(any(KnowledgeBaseRagQaQuery.class))).thenReturn(qaAnswer(false));

        var response = service.run(7L, 99L, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDecision()).isEqualTo("rag_tool");
        assertThat(response.getFinalAnswer()).isEqualTo("Safe grounded answer");
        assertThat(response.isNoEvidence()).isFalse();
        assertThat(response.isFallbackUsed()).isFalse();
        assertThat(response.getAnswerProvider()).isEqualTo("mock");
        assertThat(response.getAnswerModel()).isEqualTo("mock-kb");
        assertThat(response.getModelCallCount()).isEqualTo(1);
        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getRetrievalHits()).hasSize(1);
        assertThat(response.getDocumentHitCounts()).containsEntry(201L, 1);
        assertThat(response.getSteps()).hasSize(1);
        assertThat(response.getSteps().get(0).getToolName()).isEqualTo("knowledge_base_rag_qa");
        assertThat(response.getSteps().get(0).getInputSummary())
                .contains("sessionIdPresent=true")
                .doesNotContain("answer with evidence")
                .doesNotContain("Safe grounded answer")
                .doesNotContain(RAW_DOCUMENT_MARKER);
        assertThat(response.getSteps().get(0).getOutputSummary())
                .contains("citationCount=1")
                .contains("modelCallCount=1")
                .doesNotContain("Safe grounded answer")
                .doesNotContain(RAW_DOCUMENT_MARKER);
        verify(toolCallService, never()).call(any(), any());

        ArgumentCaptor<KnowledgeBaseRagQaQuery> captor = ArgumentCaptor.forClass(KnowledgeBaseRagQaQuery.class);
        verify(knowledgeBaseRagQaService).answer(captor.capture());
        KnowledgeBaseRagQaQuery query = captor.getValue();
        assertThat(query.userId()).isEqualTo(7L);
        assertThat(query.knowledgeBaseId()).isEqualTo(99L);
        assertThat(query.question()).isEqualTo("answer with evidence");
        assertThat(query.topK()).isEqualTo(5);
        assertThat(query.indexVersion()).isEqualTo(3);
        assertThat(query.sessionId()).isEqualTo("session-a");
        assertThat(query.multiQueryEnabled()).isTrue();
        assertThat(query.maxQueryVariants()).isEqualTo(4);
    }

    @Test
    void shouldRunKnowledgeBaseRagQaForSummaryIntent() {
        KnowledgeBaseAgentServiceImpl service = service();
        KnowledgeBaseAgentRequest request = request("summarize the knowledge base");
        when(knowledgeBaseRagQaService.answer(any(KnowledgeBaseRagQaQuery.class))).thenReturn(qaAnswer(false));

        var response = service.run(7L, 99L, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDecision()).isEqualTo("summary_tool");
        assertThat(response.getSteps()).hasSize(1);
        assertThat(response.getSteps().get(0).getToolName()).isEqualTo("knowledge_base_rag_qa");
        verify(toolCallService, never()).call(any(), any());
        verify(knowledgeBaseRagQaService).answer(any(KnowledgeBaseRagQaQuery.class));
    }

    @Test
    void shouldReturnNoEvidenceFromKnowledgeBaseRagQa() {
        KnowledgeBaseAgentServiceImpl service = service();
        KnowledgeBaseAgentRequest request = request("answer with evidence");
        when(knowledgeBaseRagQaService.answer(any(KnowledgeBaseRagQaQuery.class))).thenReturn(qaAnswer(true));

        var response = service.run(7L, 99L, request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getDecision()).isEqualTo("rag_tool");
        assertThat(response.isNoEvidence()).isTrue();
        assertThat(response.getCitations()).isEmpty();
        assertThat(response.getRetrievalHits()).isEmpty();
        assertThat(response.getSteps()).hasSize(1);
        assertThat(response.getSteps().get(0).getStatus()).isEqualTo("review");
    }

    @Test
    void shouldRethrowKnowledgeBaseScopeFailure() {
        KnowledgeBaseAgentServiceImpl service = service();
        KnowledgeBaseAgentRequest request = request("retrieve evidence chunks");
        when(toolCallService.call(eq(7L), any(ToolCallRequest.class))).thenReturn(ToolCallResult.failed(
                KnowledgeBaseSearchTool.TOOL_NAME,
                ErrorCode.KNOWLEDGE_BASE_FORBIDDEN.name(),
                ErrorCode.KNOWLEDGE_BASE_FORBIDDEN.name()
        ));

        assertThatThrownBy(() -> service.run(7L, 99L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN);
    }

    @Test
    void shouldRejectBlankTaskAndInvalidKnowledgeBaseId() {
        KnowledgeBaseAgentServiceImpl service = service();

        assertThatThrownBy(() -> service.run(7L, 0L, request("retrieve evidence")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> service.run(7L, 99L, request(" ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void shouldRethrowKnowledgeBaseScopeFailureFromRagQa() {
        KnowledgeBaseAgentServiceImpl service = service();
        KnowledgeBaseAgentRequest request = request("answer with evidence");
        when(knowledgeBaseRagQaService.answer(any(KnowledgeBaseRagQaQuery.class)))
                .thenThrow(new BusinessException(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN));

        assertThatThrownBy(() -> service.run(7L, 99L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KNOWLEDGE_BASE_FORBIDDEN);
    }

    private KnowledgeBaseAgentServiceImpl service() {
        return new KnowledgeBaseAgentServiceImpl(
                toolCallService,
                new DocumentToolSelector(),
                knowledgeBaseRagQaService
        );
    }

    private KnowledgeBaseAgentRequest request(String task) {
        KnowledgeBaseAgentRequest request = new KnowledgeBaseAgentRequest();
        request.setTask(task);
        return request;
    }

    private KnowledgeBaseSearchTool.SearchResult searchResult(boolean noEvidence) {
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
                99L,
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
                7L,
                99L,
                "",
                4,
                2,
                List.of(201L, 202L),
                Map.of(201L, 1, 202L, 0),
                noEvidence,
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

    private KnowledgeBaseRagQaAnswer qaAnswer(boolean noEvidence) {
        KnowledgeBaseRagRetrievalResult retrieval = noEvidence
                ? new KnowledgeBaseRagRetrievalResult(
                7L,
                99L,
                "",
                4,
                2,
                List.of(201L, 202L),
                List.of(),
                List.of(),
                true,
                "mock",
                "collection",
                "embedding",
                Map.of(201L, 0, 202L, 0),
                "hybrid",
                true,
                "rerank-model",
                true,
                3,
                8
        )
                : new KnowledgeBaseRagRetrievalResult(
                7L,
                99L,
                "",
                4,
                2,
                List.of(201L, 202L),
                List.of(ragHit()),
                List.of(ragHit().toCitation()),
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
                7L,
                99L,
                "",
                noEvidence ? "未在当前知识库索引中检索到足够证据。" : "Safe grounded answer",
                "session-a",
                retrieval,
                noEvidence,
                false,
                "",
                "mock",
                "mock-kb",
                noEvidence ? 0 : 1
        );
    }

    private KnowledgeBaseRagRetrievalHit ragHit() {
        return new KnowledgeBaseRagRetrievalHit(
                1,
                99L,
                "vector-1",
                0.91d,
                7L,
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
    }
}
