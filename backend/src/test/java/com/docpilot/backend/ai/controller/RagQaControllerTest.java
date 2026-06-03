package com.docpilot.backend.ai.controller;

import com.docpilot.backend.ai.dto.RagQaRequest;
import com.docpilot.backend.ai.dto.RagRetrieveRequest;
import com.docpilot.backend.ai.rag.RagQaAnswer;
import com.docpilot.backend.ai.rag.RagQaQuery;
import com.docpilot.backend.ai.rag.RagRetrievalQuery;
import com.docpilot.backend.ai.rag.RagRetrievalResult;
import com.docpilot.backend.ai.service.RagDocumentRetrievalService;
import com.docpilot.backend.ai.service.RagQaService;
import com.docpilot.backend.common.context.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagQaControllerTest {

    private final RagDocumentRetrievalService retrievalService = mock(RagDocumentRetrievalService.class);
    private final RagQaService ragQaService = mock(RagQaService.class);
    private final RagQaController controller = new RagQaController(retrievalService, ragQaService);

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void shouldMapRetrieveRequestToServiceQuery() {
        UserHolder.setUserId(7L);
        when(retrievalService.retrieve(org.mockito.ArgumentMatchers.any())).thenReturn(retrievalResult());
        RagRetrieveRequest request = new RagRetrieveRequest();
        request.setDocumentId(101L);
        request.setQuery("cache");
        request.setTopK(5);
        request.setIndexVersion(2);

        controller.retrieve(request);

        ArgumentCaptor<RagRetrievalQuery> captor = ArgumentCaptor.forClass(RagRetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().documentId()).isEqualTo(101L);
        assertThat(captor.getValue().query()).isEqualTo("cache");
        assertThat(captor.getValue().topK()).isEqualTo(5);
        assertThat(captor.getValue().indexVersion()).isEqualTo(2);
    }

    @Test
    void shouldUsePathDocumentIdForRagQa() {
        UserHolder.setUserId(7L);
        when(ragQaService.answer(org.mockito.ArgumentMatchers.any())).thenReturn(qaAnswer());
        RagQaRequest request = new RagQaRequest();
        request.setQuestion("cache?");
        request.setTopK(3);
        request.setIndexVersion(1);
        request.setSessionId("s1");

        controller.qa(101L, request);

        ArgumentCaptor<RagQaQuery> captor = ArgumentCaptor.forClass(RagQaQuery.class);
        verify(ragQaService).answer(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().documentId()).isEqualTo(101L);
        assertThat(captor.getValue().question()).isEqualTo("cache?");
        assertThat(captor.getValue().sessionId()).isEqualTo("s1");
    }

    @Test
    void shouldExposeIndependentRagStreamEndpoint() {
        UserHolder.setUserId(7L);
        SseEmitter emitter = new SseEmitter();
        when(ragQaService.streamAnswer(org.mockito.ArgumentMatchers.any())).thenReturn(emitter);
        RagQaRequest request = new RagQaRequest();
        request.setQuestion("stream?");

        SseEmitter result = controller.qaStream(101L, request);

        assertThat(result).isSameAs(emitter);
        verify(ragQaService).streamAnswer(org.mockito.ArgumentMatchers.any());
    }

    private RagRetrievalResult retrievalResult() {
        return new RagRetrievalResult(7L, 101L, "cache", 3, 1, List.of(), List.of(), true, "in_memory", "", "");
    }

    private RagQaAnswer qaAnswer() {
        return new RagQaAnswer(7L, 101L, "cache?", "answer", "s1", retrievalResult(), true, true, "no_evidence");
    }
}
