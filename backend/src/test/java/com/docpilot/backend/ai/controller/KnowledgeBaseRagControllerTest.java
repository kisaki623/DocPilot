package com.docpilot.backend.ai.controller;

import com.docpilot.backend.ai.dto.KnowledgeBaseRagQaRequest;
import com.docpilot.backend.ai.dto.KnowledgeBaseRagRetrieveRequest;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaAnswer;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagQaQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalQuery;
import com.docpilot.backend.ai.rag.KnowledgeBaseRagRetrievalResult;
import com.docpilot.backend.ai.service.KnowledgeBaseRagQaService;
import com.docpilot.backend.ai.service.KnowledgeBaseRagRetrievalService;
import com.docpilot.backend.ai.vo.KnowledgeBaseRagQaResponse;
import com.docpilot.backend.common.context.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeBaseRagControllerTest {

    private final KnowledgeBaseRagRetrievalService retrievalService = mock(KnowledgeBaseRagRetrievalService.class);
    private final KnowledgeBaseRagQaService qaService = mock(KnowledgeBaseRagQaService.class);
    private final KnowledgeBaseRagController controller = new KnowledgeBaseRagController(retrievalService, qaService);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldMapRetrieveRequestToServiceQuery() {
        UserHolder.setUserId(7L);
        when(retrievalService.retrieve(org.mockito.Mockito.any())).thenReturn(retrieval());
        KnowledgeBaseRagRetrieveRequest request = new KnowledgeBaseRagRetrieveRequest();
        request.setQuery("cache");
        request.setTopK(5);
        request.setIndexVersion(2);

        controller.retrieve(10L, request);

        ArgumentCaptor<KnowledgeBaseRagRetrievalQuery> captor =
                ArgumentCaptor.forClass(KnowledgeBaseRagRetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().knowledgeBaseId()).isEqualTo(10L);
        assertThat(captor.getValue().query()).isEqualTo("cache");
        assertThat(captor.getValue().topK()).isEqualTo(5);
        assertThat(captor.getValue().indexVersion()).isEqualTo(2);
    }

    @Test
    void shouldMapQaRequestToServiceQuery() {
        UserHolder.setUserId(7L);
        when(qaService.answer(org.mockito.Mockito.any())).thenReturn(answer());
        KnowledgeBaseRagQaRequest request = new KnowledgeBaseRagQaRequest();
        request.setQuestion("cache?");
        request.setTopK(3);
        request.setIndexVersion(1);
        request.setSessionId("s1");

        KnowledgeBaseRagQaResponse response = controller.qa(10L, request).data();

        ArgumentCaptor<KnowledgeBaseRagQaQuery> captor =
                ArgumentCaptor.forClass(KnowledgeBaseRagQaQuery.class);
        verify(qaService).answer(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().knowledgeBaseId()).isEqualTo(10L);
        assertThat(captor.getValue().question()).isEqualTo("cache?");
        assertThat(captor.getValue().sessionId()).isEqualTo("s1");
        assertThat(response.getAudit()).isNotNull();
        assertThat(response.getAudit().fallbackReason()).isEqualTo("no_evidence");
    }

    private KnowledgeBaseRagRetrievalResult retrieval() {
        return new KnowledgeBaseRagRetrievalResult(
                7L,
                10L,
                "cache",
                3,
                1,
                List.of(101L),
                List.of(),
                List.of(),
                true,
                "in_memory",
                "",
                "mock-model",
                Map.of(101L, 0)
        );
    }

    private KnowledgeBaseRagQaAnswer answer() {
        return new KnowledgeBaseRagQaAnswer(
                7L,
                10L,
                "cache?",
                "answer",
                "s1",
                retrieval(),
                true,
                true,
                "no_evidence",
                "mock",
                "mock",
                0
        );
    }
}
