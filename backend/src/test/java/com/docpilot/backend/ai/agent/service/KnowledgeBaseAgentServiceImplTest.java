package com.docpilot.backend.ai.agent.service;

import com.docpilot.backend.ai.agent.dto.KnowledgeBaseAgentRequest;
import com.docpilot.backend.ai.agent.dto.ToolCallRequest;
import com.docpilot.backend.ai.agent.service.impl.KnowledgeBaseAgentServiceImpl;
import com.docpilot.backend.ai.agent.tool.DocumentToolSelector;
import com.docpilot.backend.ai.agent.tool.KnowledgeBaseSearchTool;
import com.docpilot.backend.ai.agent.tool.spec.ToolCallResult;
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

    @Test
    void shouldRunKnowledgeBaseSearchToolForRetrievalOnlyIntent() {
        KnowledgeBaseAgentServiceImpl service = new KnowledgeBaseAgentServiceImpl(
                toolCallService,
                new DocumentToolSelector()
        );
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
        KnowledgeBaseAgentServiceImpl service = new KnowledgeBaseAgentServiceImpl(
                toolCallService,
                new DocumentToolSelector()
        );
        KnowledgeBaseAgentRequest request = request("retrieve evidence chunks");
        KnowledgeBaseSearchTool.SearchResult searchResult = new KnowledgeBaseSearchTool.SearchResult(
                7L,
                99L,
                "",
                3,
                1,
                List.of(201L, 202L),
                Map.of(201L, 0, 202L, 0),
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
        assertThat(response.getDocumentHitCounts()).containsEntry(201L, 0).containsEntry(202L, 0);
    }

    @Test
    void shouldNotCallToolForAnswerIntentInP0() {
        KnowledgeBaseAgentServiceImpl service = new KnowledgeBaseAgentServiceImpl(
                toolCallService,
                new DocumentToolSelector()
        );
        KnowledgeBaseAgentRequest request = request("answer with evidence");

        var response = service.run(7L, 99L, request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getDecision()).isEqualTo("rag_tool");
        assertThat(response.getFinalAnswer()).contains("P0 目前仅支持检索证据");
        assertThat(response.getSteps()).isEmpty();
        verify(toolCallService, never()).call(any(), any());
    }

    @Test
    void shouldRethrowKnowledgeBaseScopeFailure() {
        KnowledgeBaseAgentServiceImpl service = new KnowledgeBaseAgentServiceImpl(
                toolCallService,
                new DocumentToolSelector()
        );
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
        KnowledgeBaseAgentServiceImpl service = new KnowledgeBaseAgentServiceImpl(
                toolCallService,
                new DocumentToolSelector()
        );

        assertThatThrownBy(() -> service.run(7L, 0L, request("retrieve evidence")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> service.run(7L, 99L, request(" ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
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
}
